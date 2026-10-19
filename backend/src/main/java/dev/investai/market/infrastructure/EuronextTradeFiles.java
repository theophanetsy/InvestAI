package dev.investai.market.infrastructure;

import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.MifirTrade;
import dev.investai.market.domain.TradeFileSegment;
import dev.investai.market.domain.TradeFileSource;
import java.net.URI;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Downloads the Euronext deferred trade files of Paris for the previous trading day: one direct GET
 * per file, identifiable user agent, no page browsing (ADR-0009). The body is read as a stream by
 * {@link MifirFileReader} while it downloads.
 */
final class EuronextTradeFiles implements TradeFileSource {

  private static final String PATH =
      "/data-reporting-service/trades-file/download/{segment}/PREVIOUS_TRADING_DAY/{market}";
  private static final String PARIS = "PAR";

  private final RestClient rest;
  private final MifirFileReader reader;

  private EuronextTradeFiles(RestClient rest, MifirFileReader reader) {
    this.rest = rest;
    this.reader = reader;
  }

  /**
   * Creates the source.
   *
   * @param builder a fresh builder (bound to a mock server in tests)
   * @param baseUrl e.g. {@code https://marketdata.euronext.com}
   * @param userAgent identifiable user agent (spec 2.1, criterion 14)
   * @param reader the file reader
   * @return the source
   */
  static EuronextTradeFiles create(
      RestClient.Builder builder, URI baseUrl, String userAgent, MifirFileReader reader) {
    return new EuronextTradeFiles(
        builder.baseUrl(baseUrl).defaultHeader(HttpHeaders.USER_AGENT, userAgent).build(), reader);
  }

  @Override
  public Summary read(TradeFileSegment segment, Consumer<MifirTrade> trades) {
    String label = segment.name();
    return HttpCalls.call(
        "Euronext",
        label,
        () ->
            rest.get()
                .uri(PATH, label, PARIS)
                .exchange(
                    (request, response) -> {
                      if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new MarketDataUnavailableException(
                            "Euronext "
                                + label
                                + " failed: HTTP "
                                + response.getStatusCode().value());
                      }
                      return reader.read(label, response.getBody(), trades);
                    }));
  }
}
