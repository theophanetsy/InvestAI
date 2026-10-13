#!/usr/bin/env node
// Cross-platform launcher for the backend Maven wrapper (npm scripts run under cmd.exe on Windows).
// No shell option: the wrapper and its arguments are passed as an argv array.
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const backendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'backend');
const args = process.argv.slice(2);

const [command, commandArgs] =
  process.platform === 'win32'
    ? [process.env.ComSpec ?? 'cmd.exe', ['/d', '/c', path.join(backendDir, 'mvnw.cmd'), ...args]]
    : [path.join(backendDir, 'mvnw'), args];

const result = spawnSync(command, commandArgs, { cwd: backendDir, stdio: 'inherit' });
process.exit(result.status ?? 1);
