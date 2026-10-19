package dev.investai.market.infrastructure;

import dev.investai.market.domain.MarketDataUnavailableException;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Runs a provider call and turns any HTTP client failure into a {@link
 * MarketDataUnavailableException} whose message holds only the provider, the operation and the
 * status or failure type. Spring messages ({@code I/O error on GET request for "<url>"}), response
 * bodies and causes are never kept: they may contain the URL, hence the API key (spec 2.1,
 * criterion 15).
 */
final class HttpCalls {

  private HttpCalls() {}

  /**
   * Calls a provider.
   *
   * @param provider provider name, e.g. {@code EODHD}
   * @param operation operation name, e.g. {@code eod}
   * @param call the request
   * @param <T> body type
   * @return the body, never null
   * @throws MarketDataUnavailableException on any failure, including an empty body
   */
  static <T> T call(String provider, String operation, Supplier<@Nullable T> call) {
    String prefix = provider + " " + operation + " failed: ";
    T body;
    try {
      body = call.get();
    } catch (RestClientResponseException ex) {
      throw new MarketDataUnavailableException(
          prefix + "HTTP " + ex.getStatusCode().value(), RedactedCause.of(ex));
    } catch (RestClientException ex) {
      throw new MarketDataUnavailableException(
          prefix + ex.getClass().getSimpleName(), RedactedCause.of(ex));
    }
    if (body == null) {
      throw new MarketDataUnavailableException(prefix + "empty response");
    }
    return body;
  }
}
