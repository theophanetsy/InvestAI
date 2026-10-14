#!/usr/bin/env node
// Refreshes the API contract and the Angular client after an API change (ADR-0004):
//   1. copies backend/target/openapi.json (written by OpenApiContractIT) to api/openapi.json;
//   2. regenerates the client (openapi-generator-maven-plugin, execution "angular-client");
//   3. replaces frontend/src/app/api/generated with the generated TypeScript files.
// Prerequisite: a backend build that ran the integration tests (`cd backend && ./mvnw verify`,
// Docker required: `-DskipITs` does not produce backend/target/openapi.json).
import { spawnSync } from 'node:child_process';
import { copyFileSync, cpSync, existsSync, rmSync, statSync } from 'node:fs';
import { basename, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const served = join(root, 'backend', 'target', 'openapi.json');
const contract = join(root, 'api', 'openapi.json');
const generated = join(root, 'backend', 'target', 'angular-client');
const client = join(root, 'frontend', 'src', 'app', 'api', 'generated');

if (!existsSync(served)) {
  console.error(
    'backend/target/openapi.json absent : lancer `cd backend && ./mvnw verify` avant ' +
      '(Docker requis : écrit par OpenApiContractIT, pas produit avec -DskipITs).',
  );
  process.exit(1);
}
copyFileSync(served, contract);

const backend = join(root, 'backend');
const mavenArgs = ['-q', 'openapi-generator:generate@angular-client'];
// No `shell: true` (Semgrep): on Windows, a .cmd file only runs through cmd.exe, called explicitly.
const [command, args] =
  process.platform === 'win32'
    ? ['cmd.exe', ['/d', '/c', join(backend, 'mvnw.cmd'), ...mavenArgs]]
    : [join(backend, 'mvnw'), mavenArgs];
const generation = spawnSync(command, args, { cwd: backend, stdio: 'inherit' });
if (generation.status !== 0) {
  process.exit(generation.status ?? 1);
}
if (!existsSync(generated)) {
  console.error('backend/target/angular-client absent après génération : client inchangé.');
  process.exit(1);
}

rmSync(client, { recursive: true, force: true });
// TypeScript sources only: no README, scripts or generator metadata in the frontend.
const keep = (source) =>
  statSync(source).isDirectory() ? !basename(source).startsWith('.') : source.endsWith('.ts');
cpSync(generated, client, { recursive: true, filter: keep });
console.log('Contrat api/openapi.json et client frontend/src/app/api/generated mis à jour.');
