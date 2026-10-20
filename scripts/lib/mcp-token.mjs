// MCP bearer token of InvestAI (ADR-0010, spec 3.0, criterion 8): 256 random bits, base64url, with a
// recognisable prefix for secret scanners. The backend only knows INVESTAI_MCP_TOKEN_HASH, the
// lowercase hex SHA-256 of the token's UTF-8 bytes. Kept in sync with the backend by
// McpTokenVerifierTest (vector produced by mcpTokenHash, see FixtureCredentials).
import { createHash, randomBytes } from 'node:crypto';

export const TOKEN_PREFIX = 'investai_mcp_';
const TOKEN_BYTES = 32;

export function newMcpToken() {
  return TOKEN_PREFIX + randomBytes(TOKEN_BYTES).toString('base64url');
}

export function mcpTokenHash(token) {
  return createHash('sha256').update(token, 'utf8').digest('hex');
}
