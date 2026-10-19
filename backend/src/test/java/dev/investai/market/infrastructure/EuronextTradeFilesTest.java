package dev.investai.market.infrastructure;

import static dev.investai.market.infrastructure.MifirFixtures.csv;
import static dev.investai.market.infrastructure.MifirFixtures.trade;
import static dev.investai.market.infrastructure.MifirFixtures.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.MifirTrade;
import dev.investai.market.domain.TradeFileSegment;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EuronextTradeFilesTest {

  private static final String USER_AGENT = "InvestAI/test (personal use; +https://example.test)";
  private static final String URL =
      "https://euronext.test/data-reporting-service/trades-file/download/ETFS_FUNDS/PREVIOUS_TRADING_DAY/PAR";

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final EuronextTradeFiles files =
      EuronextTradeFiles.create(
          builder, URI.create("https://euronext.test"), USER_AGENT, new MifirFileReader(1_000_000));
  private final List<MifirTrade> trades = new ArrayList<>();

  @Test
  void downloadsThePreviousTradingDayFileOfParisWithAnIdentifiableUserAgent() {
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("User-Agent", USER_AGENT))
        .andRespond(
            withSuccess(
                zip(csv(trade("2026-10-16T15:35:12Z", "LU1681043599", "709.5500000", "1", "XPAR"))),
                MediaType.parseMediaType("application/zip")));

    var summary = files.read(TradeFileSegment.ETFS_FUNDS, trades::add);

    assertThat(summary.session()).isEqualTo(LocalDate.parse("2026-10-16"));
    assertThat(trades).hasSize(1);
    server.verify();
  }

  @Test
  void httpErrorIsAFailureWithoutTheUrl() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> files.read(TradeFileSegment.ETFS_FUNDS, trades::add))
        .isInstanceOf(MarketDataUnavailableException.class)
        .hasMessage("Euronext ETFS_FUNDS failed: HTTP 503");
  }

  @Test
  void networkErrorIsAFailureWithoutTheUrl() {
    server.expect(requestTo(URL)).andRespond(withException(new IOException("reset " + URL)));

    assertThatThrownBy(() -> files.read(TradeFileSegment.ETFS_FUNDS, trades::add))
        .isInstanceOf(MarketDataUnavailableException.class)
        .hasMessage("Euronext ETFS_FUNDS failed: ResourceAccessException")
        .hasStackTraceContaining("message redacted")
        .satisfies(ex -> assertThat(ex.getCause()).hasNoCause());
  }
}
