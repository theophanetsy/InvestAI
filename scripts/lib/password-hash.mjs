// INVESTAI_PASSWORD_HASH format (ADR-0007): Spring Security's
// Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(), i.e.
// "{pbkdf2@SpringSecurity_v5_8}" + hex(salt[16] + PBKDF2-HMAC-SHA256(password, salt, 310000)[32]).
// Kept in sync with the backend by SingleUserConfigurationTest (vector produced by
// scripts/hash-password.mjs, see FixtureCredentials).
import { pbkdf2Sync, randomBytes } from 'node:crypto';

const PREFIX = '{pbkdf2@SpringSecurity_v5_8}';
const SALT_BYTES = 16;
const ITERATIONS = 310_000;
const HASH_BYTES = 32;

export function hashPassword(password) {
  const salt = randomBytes(SALT_BYTES);
  const hash = pbkdf2Sync(password, salt, ITERATIONS, HASH_BYTES, 'sha256');
  return PREFIX + Buffer.concat([salt, hash]).toString('hex');
}
