#!/usr/bin/env node
// Prints the INVESTAI_PASSWORD_HASH value for a password (ADR-0007).
//
//   node scripts/hash-password.mjs                 # interactive: hidden prompt, asked twice
//   winpty node scripts/hash-password.mjs          # same, from Git Bash / mintty on Windows
//   printf '%s' "$PASSWORD" | node scripts/hash-password.mjs
//
// The password is never read from the command line (shell history, process list). Without a
// terminal (pipe, or mintty without winpty), it is read from stdin, unmasked: a warning says so.
// Format and parameters: scripts/lib/password-hash.mjs.
import { stdin, stdout, stderr, exit } from 'node:process';
import { hashPassword } from './lib/password-hash.mjs';

const MIN_LENGTH = 12;
// LoginRequest accepts at most 200 UTF-16 code units: a longer password could never log in.
const MAX_LENGTH = 200;
const CONTROL_CHARACTERS = /[\u0000-\u001f\u007f]/;

function readHidden(prompt) {
  return new Promise((resolve) => {
    stderr.write(prompt);
    let value = '';
    stdin.setRawMode(true);
    stdin.setEncoding('utf8');
    stdin.resume();
    const onData = (chunk) => {
      for (const char of chunk) {
        if (char === '\u0003') {
          stderr.write('\n');
          exit(130);
        } else if (char === '\r' || char === '\n' || char === '\u0004') {
          stdin.setRawMode(false);
          stdin.pause();
          stdin.off('data', onData);
          stderr.write('\n');
          resolve(value);
          return;
        } else if (char === '\u007f' || char === '\b') {
          value = value.slice(0, -1);
        } else {
          value += char;
        }
      }
    };
    stdin.on('data', onData);
  });
}

async function readPiped() {
  let value = '';
  stdin.setEncoding('utf8');
  for await (const chunk of stdin) {
    value += chunk;
  }
  return value.replace(/\r?\n$/, '');
}

async function main() {
  let password;
  if (stdin.isTTY) {
    password = await readHidden('Mot de passe : ');
    if ((await readHidden('Confirmation : ')) !== password) {
      stderr.write('Les deux saisies diffèrent.\n');
      exit(1);
    }
  } else {
    stderr.write("Lecture sur l'entrée standard (saisie non masquée ; terminer par Ctrl-D).\n");
    password = await readPiped();
  }
  if (CONTROL_CHARACTERS.test(password)) {
    stderr.write('Caractère de contrôle refusé (flèche, tabulation, échappement…).\n');
    exit(1);
  }
  if (password.length > MAX_LENGTH) {
    stderr.write(`Mot de passe trop long (maximum ${MAX_LENGTH} caractères).\n`);
    exit(1);
  }
  if ([...password].length < MIN_LENGTH) {
    stderr.write(`Mot de passe trop court (minimum ${MIN_LENGTH} caractères).\n`);
    exit(1);
  }
  stdout.write(hashPassword(password) + '\n');
}

await main();
