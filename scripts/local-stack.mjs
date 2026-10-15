#!/usr/bin/env node
// Runs an isolated copy of the whole application, to check a change in the real app (agent
// app-verifier, docs/specs/phase-1-06-app-verifier.md):
//   node scripts/local-stack.mjs start    # throwaway PostgreSQL, backend, frontend; prints the login
//   node scripts/local-stack.mjs status
//   node scripts/local-stack.mjs stop     # stops everything and deletes the throwaway database
// Never touches the development stack: own compose project and ports (15432, 18080, 14200), random
// database password and login generated at each start, infra/.env never read. Requires Docker.
// Logs and state (with the throwaway login) in .local-stack/, ignored by Git.
import { randomBytes } from 'node:crypto';
import { spawn, spawnSync } from 'node:child_process';
import {
  existsSync,
  mkdirSync,
  openSync,
  readdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { createServer } from 'node:net';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { hashPassword } from './lib/password-hash.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const stateDir = join(root, '.local-stack');
const stateFile = join(stateDir, 'state.json');
const browserDir = join(stateDir, 'browser');
const composeFile = join(root, 'infra', 'docker-compose.yml');
const PROJECT = 'investai-verify';
const PORTS = { database: 15432, backend: 18080, frontend: 14200 };
const APP_URL = `http://localhost:${PORTS.frontend}`;
const READINESS_URL = `http://127.0.0.1:${PORTS.backend}/actuator/health/readiness`;
const USERNAME = 'verifier';
const DATABASE = { name: 'investai', user: 'investai' };
const windows = process.platform === 'win32';

function fail(message) {
  console.error(message);
  process.exit(1);
}

function readState() {
  return existsSync(stateFile) ? JSON.parse(readFileSync(stateFile, 'utf8')) : null;
}

/** Never prints the password: console output ends up in terminal history and agent transcripts. */
function printLogin(state) {
  console.log(
    [
      'Stack de vérification prête (données jetables) :',
      `  URL          ${state.url}`,
      `  Identifiant  ${state.username}`,
      '  Mot de passe champ "password" de .local-stack/state.json',
      `  Journaux     .local-stack/backend.log, .local-stack/frontend.log`,
      'Arrêt : npm run stack:stop',
    ].join('\n'),
  );
}

function compose(args, env = {}) {
  // Explicit values: Compose also reads infra/.env, whose variables must not leak in.
  const result = spawnSync('docker', ['compose', '-p', PROJECT, '-f', composeFile, ...args], {
    env: { ...process.env, ...env },
    stdio: 'inherit',
  });
  return result.status === 0;
}

/** Deletes the throwaway database; the password is only needed by the compose file's interpolation. */
function removeDatabase() {
  return compose(['down', '-v'], { POSTGRES_PASSWORD: 'unused' });
}

function portIsFree(port) {
  return new Promise((resolve) => {
    const server = createServer()
      .once('error', () => resolve(false))
      .once('listening', () => server.close(() => resolve(true)));
    server.listen(port, '127.0.0.1');
  });
}

/** Starts a long-running process in the background, its output in .local-stack/<name>.log. */
function background(name, command, args, options) {
  const log = openSync(join(stateDir, `${name}.log`), 'w');
  const child = spawn(command, args, {
    ...options,
    detached: true,
    windowsHide: true,
    stdio: ['ignore', log, log],
  });
  child.unref();
  return child.pid;
}

function killTree(pid) {
  if (windows) {
    spawnSync('taskkill', ['/pid', String(pid), '/T', '/F'], { stdio: 'ignore' });
    return;
  }
  try {
    process.kill(-pid, 'SIGTERM');
  } catch {
    // Already stopped.
  }
}

function isAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

async function waitFor(url, pid, name, timeoutSeconds) {
  const deadline = Date.now() + timeoutSeconds * 1000;
  while (Date.now() < deadline) {
    if (!isAlive(pid)) {
      throw new Error(`${name} s'est arrêté : voir .local-stack/${name}.log`);
    }
    try {
      if ((await fetch(url)).ok) {
        return;
      }
    } catch {
      // Not listening yet.
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error(`${name} pas prêt après ${timeoutSeconds} s : voir .local-stack/${name}.log`);
}

function startBackend(databasePassword, password) {
  const backend = join(root, 'backend');
  const mavenArgs = ['-q', 'spring-boot:run'];
  // No `shell: true` (Semgrep): on Windows, a .cmd file only runs through cmd.exe, called explicitly.
  const [command, args] = windows
    ? ['cmd.exe', ['/d', '/c', join(backend, 'mvnw.cmd'), ...mavenArgs]]
    : [join(backend, 'mvnw'), mavenArgs];
  // Environment variables override the local profile and infra/.env (Spring precedence).
  return background('backend', command, args, {
    cwd: backend,
    env: {
      ...process.env,
      SERVER_PORT: String(PORTS.backend),
      SPRING_DOCKER_COMPOSE_ENABLED: 'false',
      SPRING_DATASOURCE_URL: `jdbc:postgresql://127.0.0.1:${PORTS.database}/${DATABASE.name}`,
      SPRING_DATASOURCE_USERNAME: DATABASE.user,
      SPRING_DATASOURCE_PASSWORD: databasePassword,
      INVESTAI_USERNAME: USERNAME,
      INVESTAI_PASSWORD_HASH: hashPassword(password),
    },
  });
}

function startFrontend() {
  const frontend = join(root, 'frontend');
  const proxyConfig = join(stateDir, 'proxy.conf.json');
  writeFileSync(
    proxyConfig,
    JSON.stringify({ '/api': { target: `http://127.0.0.1:${PORTS.backend}`, secure: false } }),
  );
  const cli = join(frontend, 'node_modules', '@angular', 'cli', 'bin', 'ng.js');
  if (!existsSync(cli)) {
    throw new Error('Angular CLI absent : lancer `cd frontend && npm ci`.');
  }
  return background(
    'frontend',
    process.execPath,
    [cli, 'serve', '--port', String(PORTS.frontend), '--proxy-config', proxyConfig],
    { cwd: frontend, env: { ...process.env, NG_CLI_ANALYTICS: 'false' } },
  );
}

async function start() {
  const running = readState();
  if (running) {
    printLogin(running);
    return;
  }
  if (spawnSync('docker', ['info'], { stdio: 'ignore' }).status !== 0) {
    fail(
      'Docker indisponible : démarrer Docker (la stack de vérification a besoin de PostgreSQL).',
    );
  }
  for (const [name, port] of Object.entries(PORTS)) {
    if (!(await portIsFree(port))) {
      fail(`Port ${port} (${name}) déjà utilisé : stack déjà lancée ailleurs ? npm run stack:stop`);
    }
  }
  mkdirSync(stateDir, { recursive: true });
  const databasePassword = randomBytes(24).toString('hex');
  const state = {
    url: APP_URL,
    username: USERNAME,
    password: randomBytes(18).toString('base64url'),
  };
  removeDatabase();
  const databaseEnv = {
    POSTGRES_DB: DATABASE.name,
    POSTGRES_USER: DATABASE.user,
    POSTGRES_PASSWORD: databasePassword,
    POSTGRES_PORT: String(PORTS.database),
  };
  if (!compose(['up', '-d', '--wait'], databaseEnv)) {
    fail('PostgreSQL ne démarre pas (docker compose).');
  }
  state.backendPid = startBackend(databasePassword, state.password);
  state.frontendPid = startFrontend();
  writeFileSync(stateFile, JSON.stringify(state, null, 2));
  try {
    await waitFor(READINESS_URL, state.backendPid, 'backend', 300);
    await waitFor(APP_URL, state.frontendPid, 'frontend', 180);
  } catch (error) {
    stop();
    fail(error.message);
  }
  printLogin(state);
}

/** Browser snapshots show field values, including the throwaway password; screenshots are kept. */
function removeBrowserSnapshots() {
  if (!existsSync(browserDir)) {
    return;
  }
  for (const file of readdirSync(browserDir)) {
    if (file.endsWith('.yml')) {
      rmSync(join(browserDir, file));
    }
  }
}

function stop() {
  const state = readState();
  if (state) {
    killTree(state.frontendPid);
    killTree(state.backendPid);
    rmSync(stateFile);
  }
  removeBrowserSnapshots();
  removeDatabase();
  console.log('Stack de vérification arrêtée, base jetable supprimée.');
}

function status() {
  const state = readState();
  if (!state) {
    console.log('Stack de vérification arrêtée.');
    return;
  }
  printLogin(state);
}

const commands = { start, stop, status };
const command = commands[process.argv[2]];
if (!command) {
  fail('Usage : node scripts/local-stack.mjs start|status|stop');
}
await command();
