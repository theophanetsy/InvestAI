#!/usr/bin/env node
// stdio gateway between Claude Desktop and the MCP server of InvestAI (ADR-0010, spec 3.0).
// Claude Desktop only launches local stdio servers; InvestAI serves Streamable HTTP on /mcp.
// Each JSON-RPC message read on stdin (one per line) is POSTed to the backend, each answer is
// written on stdout (one per line). Diagnostics go to stderr, never the token.
//
//   INVESTAI_MCP_URL         default http://127.0.0.1:8080/mcp (loopback only)
//   INVESTAI_MCP_TOKEN       the token, or else:
//   INVESTAI_MCP_TOKEN_FILE  file holding it, default ~/.investai/mcp-token (scripts/mcp-token.mjs)
//
// The token is never read from the command line (process list, client configuration). No
// dependency: Node only. Setup: docs/setup.md.
import { createInterface } from 'node:readline';
import { env, exit, stderr, stdin, stdout } from 'node:process';
import { endpoint, readToken, relay } from './lib/mcp-gateway.mjs';

const log = (text) => stderr.write(`[investai-mcp] ${text}\n`);

let url;
let token;
try {
  url = endpoint(env.INVESTAI_MCP_URL || undefined);
  token = readToken(env);
} catch (error) {
  log(error.message);
  exit(1);
}

const session = {};
const lines = createInterface({ input: stdin, crlfDelay: Infinity });
// Sequential relay: answers keep the order of the requests.
for await (const line of lines) {
  if (!line.trim()) {
    continue;
  }
  for (const reply of await relay(line, { url, token, session, log })) {
    stdout.write(`${reply}\n`);
  }
}
