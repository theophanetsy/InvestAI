#!/usr/bin/env node
// PostToolUse hook (Edit|Write|MultiEdit): formats the edited file with the project formatter.
// Never blocks: formatting problems are caught by the Stop hook and CI.
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const PRETTIER_EXTENSIONS = new Set(['.ts', '.html', '.scss', '.css', '.json', '.js', '.mjs', '.md']);
const isWindows = process.platform === 'win32';

// Absolute path: cmd.exe does not reliably resolve executables from the working directory.
function mavenWrapper(backendDir) {
  return isWindows ? `"${path.join(backendDir, 'mvnw.cmd')}"` : path.join(backendDir, 'mvnw');
}

const input = JSON.parse(readFileSync(0, 'utf8'));
const filePath = input.tool_input?.file_path;
if (!filePath) process.exit(0);

const projectDir = process.env.CLAUDE_PROJECT_DIR ?? input.cwd ?? process.cwd();
const absolute = path.resolve(projectDir, filePath);
const relative = path.relative(projectDir, absolute).split(path.sep).join('/');
const extension = path.extname(absolute);

function run(command, args, cwd) {
  spawnSync(command, args, { cwd, stdio: 'ignore', shell: isWindows, timeout: 120_000 });
}

if (relative.startsWith('frontend/') && PRETTIER_EXTENSIONS.has(extension)) {
  run('npx', ['--no-install', 'prettier', '--write', '--log-level=silent', absolute], path.join(projectDir, 'frontend'));
} else if (relative.startsWith('backend/') && extension === '.java') {
  const fileRegex = absolute.replace(/[\\^$.*+?()[\]{}|]/g, '\\$&');
  const backendDir = path.join(projectDir, 'backend');
  run(mavenWrapper(backendDir), ['-q', '-B', 'spotless:apply', `-DspotlessFiles=${fileRegex}`], backendDir);
}

process.exit(0);
