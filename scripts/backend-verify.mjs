#!/usr/bin/env node
// Runs `mvnw -B verify` in backend/ on any OS (npm scripts run under cmd.exe on Windows).
// Fixed command line on purpose: no user-controlled arguments or environment values reach the process.
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const backendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'backend');
const mavenArgs = ['-B', 'verify'];

const result =
  process.platform === 'win32'
    ? spawnSync('cmd.exe', ['/d', '/c', '.\\mvnw.cmd', ...mavenArgs], { cwd: backendDir, stdio: 'inherit' })
    : spawnSync(path.join(backendDir, 'mvnw'), mavenArgs, { cwd: backendDir, stdio: 'inherit' });

process.exit(result.status ?? 1);
