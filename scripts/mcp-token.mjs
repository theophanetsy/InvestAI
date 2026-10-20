#!/usr/bin/env node
// Generates the bearer token of the MCP server of InvestAI (ADR-0010, spec 3.0, criterion 8).
//
//   node scripts/mcp-token.mjs                    # token in ~/.investai/mcp-token
//   node scripts/mcp-token.mjs --file <path>      # elsewhere, outside the repository
//   node scripts/mcp-token.mjs --force            # replace an existing token (the old one stops working)
//
// The token is never displayed (shell history, terminal scrollback, CI logs): it is written to a
// file outside the repository, readable by the current user only. Prints the line to put in
// infra/.env: INVESTAI_MCP_TOKEN_HASH=<SHA-256 of the token>. Format: scripts/lib/mcp-token.mjs.
import { chmodSync, existsSync, mkdirSync, realpathSync, writeFileSync } from 'node:fs';
import { basename, dirname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { argv, exit, stderr, stdout } from 'node:process';
import { mcpTokenHash, newMcpToken } from './lib/mcp-token.mjs';
import { DEFAULT_TOKEN_FILE } from './lib/mcp-gateway.mjs';

const repository = realpathSync(resolve(dirname(fileURLToPath(import.meta.url)), '..'));

function fail(message) {
  stderr.write(`${message}\n`);
  exit(1);
}

function parseArguments(args) {
  const options = { file: DEFAULT_TOKEN_FILE, force: false };
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--force') {
      options.force = true;
    } else if (args[i] === '--file' && args[i + 1]) {
      options.file = resolve(args[++i]);
    } else {
      fail('Usage : node scripts/mcp-token.mjs [--file <chemin hors du dépôt>] [--force]');
    }
  }
  return options;
}

/** Whether a path is the repository or inside it (links and junctions already resolved). */
function insideRepository(path) {
  const inside = relative(repository, path);
  return !isAbsolute(inside) && inside !== '..' && !inside.startsWith(`..${sep}`);
}

/** The target with its directory resolved, links and junctions included. */
function resolvedTarget(path) {
  const directory = dirname(path);
  if (insideRepository(resolve(directory))) {
    fail('Le fichier du jeton doit être hors du dépôt (il ne doit jamais être commité).');
  }
  mkdirSync(directory, { recursive: true, mode: 0o700 });
  return join(realpathSync(directory), basename(path));
}

const options = parseArguments(argv.slice(2));
const file = resolvedTarget(options.file);
if (insideRepository(file)) {
  fail('Le fichier du jeton doit être hors du dépôt (il ne doit jamais être commité).');
}
if (existsSync(file) && !options.force) {
  fail(`${file} existe déjà : --force pour le remplacer (l'ancien jeton ne marchera plus).`);
}
const token = newMcpToken();
// 0600 on Unix, also when replacing a file; on Windows the user profile is private to the user.
writeFileSync(file, `${token}\n`, { mode: 0o600 });
chmodSync(file, 0o600);
stderr.write(`Jeton écrit dans ${file} (jamais affiché). Ligne à ajouter à infra/.env :\n`);
stdout.write(`INVESTAI_MCP_TOKEN_HASH=${mcpTokenHash(token)}\n`);
