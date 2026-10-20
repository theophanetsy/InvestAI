// stdio <-> Streamable HTTP relay of the MCP gateway (ADR-0010, spec 3.0, criterion 14). Pure
// functions and one relay function taking `fetch` as a parameter, tested by
// scripts/mcp-stdio-gateway.test.mjs. Never writes the token anywhere.
import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

export const DEFAULT_URL = 'http://127.0.0.1:8080/mcp';
export const DEFAULT_TOKEN_FILE = join(homedir(), '.investai', 'mcp-token');
const LOOPBACK_HOSTS = new Set(['127.0.0.1', 'localhost', '[::1]']);
// Characters of the tokens produced by scripts/mcp-token.mjs (base64url and the prefix).
const TOKEN_FORMAT = /^[A-Za-z0-9_-]{16,256}$/;
const REQUEST_TIMEOUT_MS = 60_000;
// JSON-RPC: implementation-defined server error, and parse error.
const GATEWAY_ERROR = -32000;
const PARSE_ERROR = -32700;

/** The MCP endpoint: plain HTTP on a loopback address only, so the token never leaves the machine. */
export function endpoint(value = DEFAULT_URL) {
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new Error('INVESTAI_MCP_URL invalide.');
  }
  if (url.protocol !== 'http:' || !LOOPBACK_HOSTS.has(url.hostname)) {
    throw new Error(
      'INVESTAI_MCP_URL doit viser InvestAI en local (http://127.0.0.1, localhost ou [::1]).',
    );
  }
  return url.href;
}

/**
 * The token, from INVESTAI_MCP_TOKEN or else from the file INVESTAI_MCP_TOKEN_FILE (default
 * ~/.investai/mcp-token). Error messages never contain the value.
 */
export function readToken(environment, readFile = (path) => readFileSync(path, 'utf8')) {
  let token = environment.INVESTAI_MCP_TOKEN;
  if (!token) {
    const file = environment.INVESTAI_MCP_TOKEN_FILE || DEFAULT_TOKEN_FILE;
    try {
      token = readFile(file);
    } catch {
      throw new Error(
        `Jeton MCP introuvable : définir INVESTAI_MCP_TOKEN ou créer ${file} (node scripts/mcp-token.mjs).`,
      );
    }
  }
  token = token.trim();
  if (!TOKEN_FORMAT.test(token)) {
    throw new Error('Jeton MCP mal formé : le régénérer avec node scripts/mcp-token.mjs.');
  }
  return token;
}

function errorResponse(id, code, message) {
  return JSON.stringify({ jsonrpc: '2.0', id, error: { code, message } });
}

/** The JSON-RPC messages of a text/event-stream body, one per `data` field. */
export function eventData(body) {
  const messages = [];
  for (const event of body.split(/\r?\n\r?\n/)) {
    const data = event
      .split(/\r?\n/)
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).replace(/^ /, ''))
      .join('\n');
    if (data) {
      messages.push(data);
    }
  }
  return messages;
}

async function problemCode(response) {
  try {
    const problem = await response.json();
    return typeof problem.code === 'string' ? ` (${problem.code})` : '';
  } catch {
    return '';
  }
}

function hint(status) {
  if (status === 401) {
    return ' : jeton refusé, vérifier le jeton de la passerelle et INVESTAI_MCP_TOKEN_HASH du backend';
  }
  if (status === 429) {
    return ' : trop de requêtes, réessayer dans une minute';
  }
  return '';
}

/**
 * Relays one line read on stdin. Returns the lines to write on stdout (zero or more JSON-RPC
 * messages) and reports diagnostics through `log` (stderr). `session.protocolVersion` is learnt
 * from the initialize response and sent back in the MCP-Protocol-Version header.
 */
export async function relay(line, { url, token, session, fetchImpl = fetch, log = () => {} }) {
  let message;
  try {
    message = JSON.parse(line);
  } catch {
    log('Message JSON-RPC illisible ignoré.');
    return [errorResponse(null, PARSE_ERROR, 'Parse error')];
  }
  const id = message?.id;
  const isRequest = id !== undefined && id !== null && typeof message.method === 'string';
  const headers = {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
    Accept: 'application/json, text/event-stream',
  };
  if (session.protocolVersion) {
    headers['MCP-Protocol-Version'] = session.protocolVersion;
  }
  let response;
  try {
    response = await fetchImpl(url, {
      method: 'POST',
      headers,
      body: line,
      redirect: 'error',
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch {
    log(`InvestAI injoignable sur ${url} : le backend tourne-t-il ?`);
    return isRequest ? [errorResponse(id, GATEWAY_ERROR, 'InvestAI injoignable')] : [];
  }
  if (response.status === 202 || response.status === 204) {
    return [];
  }
  if (!response.ok) {
    const detail = `HTTP ${response.status}${await problemCode(response)}`;
    log(`InvestAI a refusé la requête : ${detail}${hint(response.status)}.`);
    return isRequest ? [errorResponse(id, GATEWAY_ERROR, `InvestAI : ${detail}`)] : [];
  }
  const body = await response.text();
  const type = response.headers.get('content-type') ?? '';
  const payloads = type.includes('text/event-stream') ? eventData(body) : [body];
  let lines;
  try {
    lines = payloads.filter((payload) => payload.trim()).map((payload) => JSON.parse(payload));
  } catch {
    log('Réponse illisible du backend.');
    return isRequest ? [errorResponse(id, GATEWAY_ERROR, 'InvestAI : réponse illisible')] : [];
  }
  if (message.method === 'initialize') {
    const version = lines.find((reply) => reply.id === id)?.result?.protocolVersion;
    if (typeof version === 'string') {
      session.protocolVersion = version;
    }
  }
  return lines.map((reply) => JSON.stringify(reply));
}
