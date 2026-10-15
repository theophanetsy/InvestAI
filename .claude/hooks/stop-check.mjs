#!/usr/bin/env node
// Stop hook: quick quality checks on files changed in the working tree.
// Exit code 2 makes the agent continue and fix the reported problems (once per stop).
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const isWindows = process.platform === 'win32';
const input = JSON.parse(readFileSync(0, 'utf8'));
if (input.stop_hook_active) process.exit(0);

const projectDir = process.env.CLAUDE_PROJECT_DIR ?? input.cwd ?? process.cwd();
const frontendDir = path.join(projectDir, 'frontend');
const backendDir = path.join(projectDir, 'backend');

// No shell: commands and arguments are passed as an argv array.
function run(command, args, cwd) {
  const result = spawnSync(command, args, { cwd, encoding: 'utf8', timeout: 300_000 });
  return { ok: result.status === 0, output: `${result.stdout ?? ''}${result.stderr ?? ''}` };
}

function runNodeTool(binPath, args) {
  return run(process.execPath, [path.join(frontendDir, 'node_modules', ...binPath), ...args], frontendDir);
}

function runMavenWrapper(args) {
  return isWindows
    ? run('cmd.exe', ['/d', '/c', path.join(backendDir, 'mvnw.cmd'), ...args], backendDir)
    : run(path.join(backendDir, 'mvnw'), args, backendDir);
}

const status = run('git', ['status', '--porcelain', '--untracked-files=all'], projectDir);
const changed = status.output
  .split('\n')
  .filter((line) => line.length > 3 && !line.startsWith(' D') && !line.startsWith('D '))
  .map((line) => line.slice(3).trim().replace(/^"|"$/g, ''))
  .map((file) => (file.includes(' -> ') ? file.split(' -> ')[1] : file));

const frontendRelative = (pattern) =>
  changed.filter((f) => f.startsWith('frontend/') && pattern.test(f)).map((f) => f.slice('frontend/'.length));
const lintFiles = frontendRelative(/\.(ts|html)$/);
const formatFiles = frontendRelative(/\.(ts|html|scss|json|js|mjs)$/);
const backendChanged = changed.some((f) => f.startsWith('backend/') && f.endsWith('.java'));

const failures = [];
if (formatFiles.length > 0) {
  const r = runNodeTool(['prettier', 'bin', 'prettier.cjs'], ['--check', ...formatFiles]);
  if (!r.ok) failures.push(`Prettier (frontend):\n${r.output}`);
}
if (lintFiles.length > 0) {
  const r = runNodeTool(['eslint', 'bin', 'eslint.js'], ['--max-warnings=0', '--no-warn-ignored', ...lintFiles]);
  if (!r.ok) failures.push(`ESLint (frontend):\n${r.output}`);
}
if (backendChanged) {
  const r = runMavenWrapper(['-q', '-B', 'spotless:check', 'checkstyle:check']);
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
