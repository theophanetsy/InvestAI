// Tests of the MCP stdio gateway (spec 3.0, criterion 15): `node --test scripts/` (npm run check).
// The relay against the real backend is covered by McpStdioGatewayIT (backend, CI).
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { once } from 'node:events';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, it, before, after } from 'node:test';
import { endpoint, eventData, readToken, relay } from './lib/mcp-gateway.mjs';
import { mcpTokenHash, newMcpToken, TOKEN_PREFIX } from './lib/mcp-token.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const token = newMcpToken();

describe('endpoint', () => {
  it('accepts HTTP on loopback addresses only', () => {
    assert.equal(endpoint(), 'http://127.0.0.1:8080/mcp');
    assert.equal(endpoint('http://localhost:18080/mcp'), 'http://localhost:18080/mcp');
    assert.equal(endpoint('http://[::1]:8080/mcp'), 'http://[::1]:8080/mcp');
    for (const url of [
      'https://127.0.0.1/mcp',
      'http://example.com/mcp',
      'http://10.0.0.2/mcp',
      'x',
    ]) {
      assert.throws(() => endpoint(url));
    }
  });
});

describe('readToken', () => {
  it('prefers the variable, then the file, and never echoes the value', () => {
    assert.equal(readToken({ INVESTAI_MCP_TOKEN: `${token}\n` }), token);
    assert.equal(
      readToken({ INVESTAI_MCP_TOKEN_FILE: 'token-file' }, (path) =>
        path === 'token-file' ? `${token}\r\n` : '',
      ),
      token,
    );
    assert.throws(
      () =>
        readToken({}, () => {
          throw new Error('ENOENT');
        }),
      /introuvable/,
    );
    const malformed = 'bad token with spaces';
    assert.throws(
      () => readToken({ INVESTAI_MCP_TOKEN: malformed }),
      (error) => !error.message.includes(malformed),
    );
  });
});

describe('token script library', () => {
  it('produces prefixed 256-bit tokens and their SHA-256', () => {
    assert.match(token, new RegExp(`^${TOKEN_PREFIX}[A-Za-z0-9_-]{43}$`));
    assert.notEqual(newMcpToken(), token);
    assert.match(mcpTokenHash(token), /^[0-9a-f]{64}$/);
    // Test vector shared with the backend (FixtureCredentials.MCP_HASH).
    assert.equal(
      mcpTokenHash('investai_mcp_vecteur-de-test-pas-un-secret'),
      '158823ba2ef89108205c80520ed342a297c1063d3e9a05f36c1a98c5bb6955ec',
    );
  });
});

describe('eventData', () => {
  it('extracts one message per event', () => {
    const body = 'event: message\ndata: {"id":1}\n\ndata: {"id":2}\r\n\r\n: comment\n\n';
    assert.deepEqual(eventData(body), ['{"id":1}', '{"id":2}']);
  });
});

/** A fake backend answering each request with the next scripted reply. */
function fakeBackend(replies) {
  const seen = [];
  const server = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    seen.push({ headers: request.headers, body });
    const { status, type = 'application/json', text = '' } = replies.shift();
    response.writeHead(status, { 'Content-Type': type });
    response.end(text);
  });
  return { server, seen };
}

describe('relay', () => {
  let backend;
  let url;
  const scripted = [];

  before(async () => {
    backend = fakeBackend(scripted);
    backend.server.listen(0, '127.0.0.1');
    await once(backend.server, 'listening');
    url = `http://127.0.0.1:${backend.server.address().port}/mcp`;
  });

  after(() => backend.server.close());

  it('forwards with the token, learns the protocol version and relays JSON and SSE answers', async () => {
    const session = {};
    scripted.push(
      { status: 200, text: '{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18"}}' },
      { status: 202 },
      {
        status: 200,
        type: 'text/event-stream',
        text: 'data: {"jsonrpc":"2.0","id":2,"result":{}}\n\n',
      },
    );

    const init = await relay('{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}', {
      url,
      token,
      session,
    });
    const notification = await relay('{"jsonrpc":"2.0","method":"notifications/initialized"}', {
      url,
      token,
      session,
    });
    const list = await relay('{"jsonrpc":"2.0","id":2,"method":"tools/list"}', {
      url,
      token,
      session,
    });

    assert.deepEqual(init.map(JSON.parse), [
      { jsonrpc: '2.0', id: 1, result: { protocolVersion: '2025-06-18' } },
    ]);
    assert.deepEqual(notification, []);
    assert.deepEqual(list.map(JSON.parse), [{ jsonrpc: '2.0', id: 2, result: {} }]);
    const [first, , third] = backend.seen.slice(-3);
    assert.equal(first.headers.authorization, `Bearer ${token}`);
    assert.equal(first.headers.accept, 'application/json, text/event-stream');
    assert.equal(first.headers.origin, undefined);
    assert.equal(first.headers['mcp-protocol-version'], undefined);
    assert.equal(third.headers['mcp-protocol-version'], '2025-06-18');
  });

  it('turns an HTTP refusal into a JSON-RPC error without the token', async () => {
    const logs = [];
    scripted.push({
      status: 401,
      type: 'application/problem+json',
      text: '{"code":"auth.required"}',
    });

    const replies = await relay('{"jsonrpc":"2.0","id":7,"method":"tools/list"}', {
      url,
      token,
      session: {},
      log: (line) => logs.push(line),
    });

    const [error] = replies.map(JSON.parse);
    assert.equal(error.id, 7);
    assert.equal(error.error.code, -32000);
    assert.match(error.error.message, /HTTP 401 \(auth\.required\)/);
    assert.ok([...replies, ...logs].every((text) => !text.includes(token)));
  });

  it('reports an unreachable backend only for requests', async () => {
    const closed = 'http://127.0.0.1:9/mcp';
    const request = await relay('{"jsonrpc":"2.0","id":"a","method":"ping"}', {
      url: closed,
      token,
      session: {},
    });
    const notification = await relay('{"jsonrpc":"2.0","method":"notifications/cancelled"}', {
      url: closed,
      token,
      session: {},
    });

    assert.equal(JSON.parse(request[0]).error.message, 'InvestAI injoignable');
    assert.deepEqual(notification, []);
  });

  it('answers a parse error to an unreadable line', async () => {
    const [reply] = await relay('{not json', { url, token, session: {} });

    assert.equal(JSON.parse(reply).error.code, -32700);
  });
});

describe('token script', () => {
  const script = join(here, 'mcp-token.mjs');
  const run = (args) => spawnSync(process.execPath, [script, ...args], { encoding: 'utf8' });

  it('writes the token outside the repository and prints only its hash', () => {
    const directory = mkdtempSync(join(tmpdir(), 'investai-token-'));
    const file = join(directory, 'nested', 'mcp-token');
    try {
      const first = run(['--file', file]);
      const written = readFileSync(file, 'utf8').trim();

      assert.equal(first.status, 0);
      assert.equal(first.stdout, `INVESTAI_MCP_TOKEN_HASH=${mcpTokenHash(written)}\n`);
      assert.ok(!first.stdout.includes(written) && !first.stderr.includes(written));
      // Never overwritten by accident; --force replaces it.
      assert.equal(run(['--file', file]).status, 1);
      assert.equal(readFileSync(file, 'utf8').trim(), written);
      assert.equal(run(['--file', file, '--force']).status, 0);
      assert.notEqual(readFileSync(file, 'utf8').trim(), written);
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  });

  it('refuses a file inside the repository', () => {
    for (const inside of [join(here, 'mcp-token-in-repo'), join(here, '..', 'mcp-token-in-repo')]) {
      const result = run(['--file', inside]);

      assert.equal(result.status, 1);
      assert.equal(result.stdout, '');
      assert.ok(!existsSync(inside));
    }
  });

  it('refuses a directory of the repository whose name starts with two dots', () => {
    // Its relative path ("..jeton/…") starts with ".." too: a naive check would let it through.
    const directory = join(here, '..', '..jeton-inside');
    const result = run(['--file', join(directory, 'mcp-token')]);

    assert.equal(result.status, 1);
    assert.equal(result.stdout, '');
    assert.ok(!existsSync(directory));
  });
});

describe('gateway process', () => {
  it('refuses to start without a token, without echoing anything on stdout', async () => {
    const child = spawn(process.execPath, [join(here, 'mcp-stdio-gateway.mjs')], {
      env: {
        ...process.env,
        INVESTAI_MCP_TOKEN: '',
        INVESTAI_MCP_TOKEN_FILE: join(here, 'absent-token-file'),
      },
    });
    let out = '';
    child.stdout.on('data', (chunk) => (out += chunk));
    const [code] = await once(child, 'exit');

    assert.equal(code, 1);
    assert.equal(out, '');
  });
});
