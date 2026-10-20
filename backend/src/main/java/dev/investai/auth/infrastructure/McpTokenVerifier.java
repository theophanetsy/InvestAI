package dev.investai.auth.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Checks the bearer token of the MCP client (ADR-0010) against {@code INVESTAI_MCP_TOKEN_HASH}, the
 * SHA-256 of the token produced by {@code scripts/mcp-token.mjs}: the backend never knows the token
 * itself. Comparison in constant time.
 */
final class McpTokenVerifier {

  /**
   * Startup failure message; never includes the value, which may be the token pasted by mistake.
   */
  static final String MALFORMED_HASH =
      "INVESTAI_MCP_TOKEN_HASH is not a SHA-256 hash produced by scripts/mcp-token.mjs";

  /** Longer bearers are rejected before hashing (the script's tokens have 56 characters). */
  static final int MAX_TOKEN_LENGTH = 256;

  private static final Pattern HASH_FORMAT = Pattern.compile("[0-9a-f]{64}");

  private final byte[] expected;

  private McpTokenVerifier(byte[] expected) {
    this.expected = expected;
  }

  /**
   * Creates the verifier. Checked here rather than by Bean Validation: binding errors print the
   * rejected value.
   *
   * @param hash lowercase hexadecimal SHA-256 of the token
   * @return the verifier
   * @throws IllegalStateException {@link #MALFORMED_HASH} when the hash has another format
   */
  static McpTokenVerifier of(String hash) {
    if (!HASH_FORMAT.matcher(hash).matches()) {
      throw new IllegalStateException(MALFORMED_HASH);
    }
    return new McpTokenVerifier(HexFormat.of().parseHex(hash));
  }

  /**
   * Tells whether a bearer is the configured token.
   *
   * @param token the presented bearer
   * @return {@code true} for the configured token only
   */
  boolean matches(String token) {
    return token.length() <= MAX_TOKEN_LENGTH && MessageDigest.isEqual(expected, sha256(token));
  }

  private static byte[] sha256(String token) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      // Every Java platform must support SHA-256 (MessageDigest Javadoc).
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
