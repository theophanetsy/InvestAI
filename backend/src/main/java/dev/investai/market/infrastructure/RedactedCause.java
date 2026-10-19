package dev.investai.market.infrastructure;

/**
 * Keeps the stack trace of an HTTP client failure without its message nor its causes: Spring's
 * {@code ResourceAccessException} and the JDK HTTP client put the full URL in their messages, and
 * the EODHD key travels in the query string.
 */
final class RedactedCause extends Exception {

  private static final long serialVersionUID = 1L;

  private RedactedCause(Throwable original) {
    super(original.getClass().getName() + " (message redacted)", null, false, true);
    setStackTrace(original.getStackTrace());
  }

  /**
   * Copies the type and stack trace of a failure.
   *
   * @param original the failure, possibly carrying a URL with credentials
   * @return a copy without message nor cause
   */
  static RedactedCause of(Throwable original) {
    return new RedactedCause(original);
  }
}
