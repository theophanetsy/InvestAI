#!/usr/bin/env node
// PreToolUse hook (Edit|Write|MultiEdit): blocks agent edits that weaken the quality gates.
// Exit code 2 blocks the tool call and sends stderr back to the agent.
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const PROTECTED_PATHS = [
  /^backend\/config\//,
  /^backend\/\.mvn\//,
  /^frontend\/eslint\.config\.js$/,
  /^frontend\/\.stylelintrc\.json$/,
  /^frontend\/tsconfig[^/]*\.json$/,
  /^frontend\/\.prettierrc$/,
  /^lefthook\.yml$/,
  /^commitlint\.config\.mjs$/,
  /^\.semgrep\//,
  /^osv-scanner\.toml$/,
  /^\.trivyignore$/,
  /^sonar-project\.properties$/,
  /^\.github\/workflows\//,
  /^\.claude\/settings\.json$/,
  /^\.claude\/hooks\//,
];

// Files where only threshold-related lines are protected.
const THRESHOLD_FILES = {
  'backend/pom.xml': /(coverage\.(line|branch)\.minimum|mutation\.threshold|<failOnWarning>|<failOnViolation>|<failOnError>|-Xep:NullAway|<threshold>|<effort>)/,
  'frontend/angular.json': /(coverageThresholds|"statements"|"branches"|"functions"|"lines")/,
  'frontend/package.json': /("lint"|"check"|"test:ci"|"audit:prod"|--max-warnings)/,
};

const SUPPRESSION_MARKERS =
  /(eslint-disable|@ts-ignore|@ts-expect-error|@ts-nocheck|NOPMD|CHECKSTYLE:OFF|@SuppressWarnings\(|@SuppressFBWarnings|istanbul ignore|v8 ignore|c8 ignore|stylelint-disable|prettier-ignore|nosemgrep|gitleaks:allow)/g;

function block(reason) {
  process.stderr.write(
    `BLOCKED by .claude/hooks/protect-quality-config.mjs: ${reason}\n` +
      'Quality gates must not be weakened by an agent. Fix the code instead. ' +
      'If you believe this is a genuine false positive, stop and ask the user to make the change.\n',
  );
  process.exit(2);
}

function countMarkers(text) {
  return (text ?? '').match(SUPPRESSION_MARKERS)?.length ?? 0;
}

const input = JSON.parse(readFileSync(0, 'utf8'));
const toolInput = input.tool_input ?? {};
const filePath = toolInput.file_path;
if (!filePath) process.exit(0);

const projectDir = process.env.CLAUDE_PROJECT_DIR ?? input.cwd ?? process.cwd();
const relative = path.relative(projectDir, path.resolve(projectDir, filePath)).split(path.sep).join('/');

if (PROTECTED_PATHS.some((pattern) => pattern.test(relative))) {
  block(`"${relative}" is a protected quality/CI configuration file.`);
}

const edits =
  input.tool_name === 'MultiEdit'
    ? (toolInput.edits ?? [])
    : input.tool_name === 'Edit'
      ? [{ old_string: toolInput.old_string, new_string: toolInput.new_string }]
      : [];

const thresholdPattern = THRESHOLD_FILES[relative];
if (thresholdPattern) {
  const touchesThreshold =
    edits.some((e) => thresholdPattern.test(e.old_string ?? '') || thresholdPattern.test(e.new_string ?? '')) ||
    (input.tool_name === 'Write' &&
      existsSync(filePath) &&
      readFileSync(filePath, 'utf8').split('\n').filter((l) => thresholdPattern.test(l)).join('\n') !==
        (toolInput.content ?? '').split('\n').filter((l) => thresholdPattern.test(l)).join('\n'));
  if (touchesThreshold) block(`edit touches quality thresholds/gates in "${relative}".`);
}

// New suppression markers in source code (existing, already-reviewed ones are tolerated).
const before =
  input.tool_name === 'Write'
    ? existsSync(filePath)
      ? countMarkers(readFileSync(filePath, 'utf8'))
      : 0
    : edits.reduce((n, e) => n + countMarkers(e.old_string), 0);
const after =
  input.tool_name === 'Write'
    ? countMarkers(toolInput.content)
    : edits.reduce((n, e) => n + countMarkers(e.new_string), 0);
if (after > before) {
  block(`edit adds a lint/analysis suppression marker in "${relative}".`);
}

process.exit(0);
