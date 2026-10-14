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

  /** Test property source entry configuring the user. */
  public static final String USERNAME_PROPERTY = "investai.auth.username=" + USERNAME;

  /** Test property source entry configuring the password hash. */
  public static final String PASSWORD_HASH_PROPERTY =
      "investai.auth.password-hash=" + PASSWORD_HASH;

  private FixtureCredentials() {}
}
