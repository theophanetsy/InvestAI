package dev.investai.auth.infrastructure;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The single InvestAI user (ADR-0007), bound from {@code INVESTAI_USERNAME} and {@code
 * INVESTAI_PASSWORD_HASH}. The application refuses to start when either is missing, or when the
 * hash is not a PBKDF2 hash produced by {@code scripts/hash-password.mjs} ({@link
 * SingleUserConfiguration}): weaker encodings such as {@code {noop}} are rejected.
 *
 * @param username the login
 * @param passwordHash the PBKDF2 hash of the password, never the password itself
 */
@Validated
@ConfigurationProperties("investai.auth")
public record AuthProperties(@NotBlank String username, @NotBlank String passwordHash) {

  @Override
  public String toString() {
    return "AuthProperties[username=" + username + ", passwordHash=<redacted>]";
  }
}
