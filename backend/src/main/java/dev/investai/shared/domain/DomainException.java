package dev.investai.shared.domain;

import java.util.regex.Pattern;

/**
 * Error raised by a use case when a request cannot be fulfilled for a business reason.
 *
 * <p>The {@link #code()} is a stable identifier sent to API clients, which translate it for the
 * user. The message is logged and never returned to the client (ADR-0006): it must only contain
 * non-sensitive technical identifiers (no IBAN, account number, amount, e-mail or user content).
 * The hierarchy is sealed so that the HTTP mapping must handle every category.
 */
public abstract sealed class DomainException extends RuntimeException
    permits ResourceNotFoundException, BusinessRuleException {

  private static final long serialVersionUID = 1L;
  private static final Pattern CODE_FORMAT =
      Pattern.compile("[a-z0-9](?:[a-z0-9]|[.-](?=[a-z0-9]))*");

  private final String code;

  /**
   * Creates the exception.
   *
   * @param code stable identifier such as {@code account.not-found}
   * @param message diagnostic message, for logs only
   */
  protected DomainException(String code, String message) {
    // Validated before super(): no partially built instance can leak from a subclass.
    if (!CODE_FORMAT.matcher(code).matches()) {
      throw new IllegalArgumentException("Invalid error code: " + code);
    }
    super(message);
    this.code = code;
  }

  /**
   * Returns the stable error code.
   *
   * @return the code, e.g. {@code account.not-found}
   */
  public String code() {
    return code;
  }
}
