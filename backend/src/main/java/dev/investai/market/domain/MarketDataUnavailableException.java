package dev.investai.market.domain;

/**
 * A market data provider could not be reached or returned unusable data. The message only names the
 * operation and the failure category: never a URL, a query string, a response body or an API key.
 */
public final class MarketDataUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception, deliberately without cause: causes may carry the full URL.
   *
   * @param message safe diagnostic message
   */
  public MarketDataUnavailableException(String message) {
    super(message);
  }
}
