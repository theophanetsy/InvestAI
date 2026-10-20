package dev.investai.support;

/**
 * The single user configured for tests (ADR-0007). The hash was produced by {@code
 * scripts/hash-password.mjs} for {@link #PASSPHRASE}: it doubles as the cross-check between the
 * script and the backend encoder. Test-only values, never used outside {@code src/test}.
 */
public final class FixtureCredentials {

  /** Configured username. */
  public static final String USERNAME = "test-user";

  /**
   * Clear password matching {@link #PASSWORD_HASH}; non-ASCII on purpose (UTF-8 check). Not named
   * PASSWORD: public test vector, not a secret (Checkstyle hard-coded secret rule, owner's call).
   */
  public static final String PASSPHRASE = "Vecteur de test — pas un secret";

  /** Output of {@code printf '%s' "$PASSPHRASE" | node scripts/hash-password.mjs}. */
  public static final String PASSWORD_HASH =
      "{pbkdf2@SpringSecurity_v5_8}366991c8b38d4965ef2170cfbba7859d"
          + "e431dfd2f540bc63e3c24dbb64ba4313fadbdb75c1637e780ad99cab3b5584df";

  /**
   * MCP bearer matching {@link #MCP_HASH}, with the prefix of {@code scripts/mcp-token.mjs} but
   * shorter than a real token, so that the gitleaks rule of {@code .gitleaks.toml} does not flag
   * it. Not named TOKEN: public test vector, not a secret (Checkstyle hard-coded secret rule, as
   * {@link #PASSPHRASE}).
   */
  public static final String MCP_BEARER = "investai_mcp_vecteur-de-test-pas-un-secret";

  /** Output of {@code mcpTokenHash(MCP_BEARER)} from {@code scripts/lib/mcp-token.mjs}. */
  public static final String MCP_HASH =
      "158823ba2ef89108205c80520ed342a297c1063d3e9a05f36c1a98c5bb6955ec";

  /** Test property source entry mounting {@code /mcp} with {@link #MCP_HASH}. */
  public static final String MCP_HASH_PROPERTY = "investai.mcp.token-hash=" + MCP_HASH;

  /** Test property source entry configuring the user. */
  public static final String USERNAME_PROPERTY = "investai.auth.username=" + USERNAME;

  /** Test property source entry configuring the password hash. */
  public static final String PASSWORD_HASH_PROPERTY =
      "investai.auth.password-hash=" + PASSWORD_HASH;

  private FixtureCredentials() {}
}
