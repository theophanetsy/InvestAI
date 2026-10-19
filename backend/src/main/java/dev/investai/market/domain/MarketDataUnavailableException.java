package dev.investai.market.domain;

/**
 * A market data provider could not be reached or returned unusable data. The message only names the
 * operation and the failure category: never a URL, a query string, a response body or an API key.
 */
public final class MarketDataUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message safe diagnostic message
   */
  public MarketDataUnavailableException(String message) {
    super(message);
  }

  /**
   * Creates the exception with a cause that the caller has stripped of any URL or credential.
   *
   * @param message safe diagnostic message
   * @param redactedCause the cause, without sensitive message
   */
  public MarketDataUnavailableException(String message, Throwable redactedCause) {
    super(message, redactedCause);
  }
}
