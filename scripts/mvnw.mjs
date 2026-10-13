#!/usr/bin/env node
// Cross-platform launcher for the backend Maven wrapper (npm scripts run under cmd.exe on Windows).
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const backendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'backend');
const isWindows = process.platform === 'win32';
const wrapper = path.join(backendDir, isWindows ? 'mvnw.cmd' : 'mvnw');

const result = spawnSync(isWindows ? `"${wrapper}"` : wrapper, process.argv.slice(2), {
  cwd: backendDir,
  stdio: 'inherit',
  shell: isWindows,
});
process.exit(result.status ?? 1);
