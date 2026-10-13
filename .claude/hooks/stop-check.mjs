#!/usr/bin/env node
// Stop hook: quick quality checks on files changed in the working tree.
// Exit code 2 makes the agent continue and fix the reported problems (once per stop).
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const isWindows = process.platform === 'win32';

// Absolute path: cmd.exe does not reliably resolve executables from the working directory.
function mavenWrapper(backendDir) {
  return isWindows ? `"${path.join(backendDir, 'mvnw.cmd')}"` : path.join(backendDir, 'mvnw');
}
const input = JSON.parse(readFileSync(0, 'utf8'));
if (input.stop_hook_active) process.exit(0);

const projectDir = process.env.CLAUDE_PROJECT_DIR ?? input.cwd ?? process.cwd();

function run(command, args, cwd) {
  const result = spawnSync(command, args, {
    cwd,
    encoding: 'utf8',
    shell: isWindows,
    timeout: 300_000,
  });
  return { ok: result.status === 0, output: `${result.stdout ?? ''}${result.stderr ?? ''}` };
}

const status = run('git', ['status', '--porcelain', '--untracked-files=all'], projectDir);
const changed = status.output
  .split('\n')
  .filter((line) => line.length > 3 && !line.startsWith(' D') && !line.startsWith('D '))
  .map((line) => line.slice(3).trim().replace(/^"|"$/g, ''))
  .map((file) => (file.includes(' -> ') ? file.split(' -> ')[1] : file));

const frontendFiles = changed
  .filter((f) => f.startsWith('frontend/') && /\.(ts|html)$/.test(f))
  .map((f) => f.slice('frontend/'.length));
const frontendFormatFiles = changed
  .filter((f) => f.startsWith('frontend/') && /\.(ts|html|scss|json|js|mjs)$/.test(f))
  .map((f) => f.slice('frontend/'.length));
const backendChanged = changed.some((f) => f.startsWith('backend/') && f.endsWith('.java'));

const failures = [];
const frontendDir = path.join(projectDir, 'frontend');
const backendDir = path.join(projectDir, 'backend');

if (frontendFormatFiles.length > 0) {
  const r = run('npx', ['--no-install', 'prettier', '--check', ...frontendFormatFiles], frontendDir);
  if (!r.ok) failures.push(`Prettier (frontend):\n${r.output}`);
}
if (frontendFiles.length > 0) {
  const r = run('npx', ['--no-install', 'eslint', '--max-warnings=0', ...frontendFiles], frontendDir);
  if (!r.ok) failures.push(`ESLint (frontend):\n${r.output}`);
}
if (backendChanged) {
  const r = run(mavenWrapper(backendDir), ['-q', '-B', 'spotless:check', 'checkstyle:check'], backendDir);
  if (!r.ok) failures.push(`Spotless/Checkstyle (backend):\n${r.output}`);
}

if (failures.length > 0) {
  const report = failures.map((f) => f.slice(-4000)).join('\n\n');
  process.stderr.write(
    `Quality checks failed on changed files. Fix them before finishing (do not disable rules):\n\n${report}\n`,
  );
  process.exit(2);
}
process.exit(0);
