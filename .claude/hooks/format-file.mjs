#!/usr/bin/env node
// PostToolUse hook (Edit|Write|MultiEdit): formats the edited file with the project formatter.
// Never blocks: formatting problems are caught by the Stop hook and CI.
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const PRETTIER_EXTENSIONS = new Set(['.ts', '.html', '.scss', '.css', '.json', '.js', '.mjs', '.md']);
// Characters interpreted by cmd.exe: such paths are never passed to the Maven wrapper.
const CMD_METACHARACTERS = /[&|<>^%!"()]/;
const isWindows = process.platform === 'win32';

const input = JSON.parse(readFileSync(0, 'utf8'));
const filePath = input.tool_input?.file_path;
if (!filePath) process.exit(0);

const projectDir = process.env.CLAUDE_PROJECT_DIR ?? input.cwd ?? process.cwd();
const absolute = path.resolve(projectDir, filePath);
const relative = path.relative(projectDir, absolute).split(path.sep).join('/');
const extension = path.extname(absolute);
const frontendDir = path.join(projectDir, 'frontend');
const backendDir = path.join(projectDir, 'backend');

// No shell: commands and arguments are passed as an argv array.
function run(command, args, cwd) {
  spawnSync(command, args, { cwd, stdio: 'ignore', timeout: 120_000 });
}

function runMavenWrapper(args) {
  if (isWindows) {
    run('cmd.exe', ['/d', '/c', path.join(backendDir, 'mvnw.cmd'), ...args], backendDir);
  } else {
    run(path.join(backendDir, 'mvnw'), args, backendDir);
  }
}

if (relative.startsWith('frontend/') && PRETTIER_EXTENSIONS.has(extension)) {
  const prettier = path.join(frontendDir, 'node_modules', 'prettier', 'bin', 'prettier.cjs');
  if (existsSync(prettier)) {
    run(process.execPath, [prettier, '--write', '--log-level=silent', absolute], frontendDir);
  }
} else if (relative.startsWith('backend/') && extension === '.java' && !CMD_METACHARACTERS.test(absolute)) {
  const fileRegex = absolute.replace(/[\\^$.*+?()[\]{}|]/g, '\\$&');
  runMavenWrapper(['-q', '-B', 'spotless:apply', `-DspotlessFiles=${fileRegex}`]);
}

process.exit(0);
