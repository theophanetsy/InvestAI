package dev.investai.market.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.investai.market.domain.HistoricalClose;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.Mic;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EodhdClientTest {

  private static final URI BASE_URL = URI.create("https://eodhd.test/api");
  private static final String USER_AGENT = "InvestAI/test (personal use; +https://example.test)";

  /** Generated: a literal would trip the hard-coded secret check, and it is not a real key. */
  private final String key = "k" + UUID.randomUUID().toString().replace("-", "");

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final EodhdClient client = EodhdClient.create(builder, BASE_URL, key, USER_AGENT);

  private final Instrument airLiquide =
      new Instrument(
          InstrumentId.newId(),
          new Listing(new Isin("FR0000120073"), Mic.XPAR),
          "AI",
          "Air Liquide",
          InstrumentType.STOCK,
          Currency.getInstance("EUR"),
          null,
          false);

  @Test
  void referenceDataComesFromTheParisSymbolListWithTheKeyAndUserAgent() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/exchange-symbol-list/PA")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("api_token", key))
        .andExpect(queryParam("fmt", "json"))
        .andExpect(header("User-Agent", USER_AGENT))
        .andRespond(
            withSuccess(
                """
                [{"Code":"AI","Name":"Air Liquide","Country":"France","Exchange":"PA",
                  "Currency":"EUR","Type":"Common Stock","Isin":"FR0000120073"},
                 {"Code":"XX","Name":"No ISIN","Country":"France","Exchange":"PA",
                  "Currency":"EUR","Type":"Common Stock","Isin":null}]
                """,
                MediaType.APPLICATION_JSON));

    var data = client.referenceData();

    assertThat(data.instruments())
        .extracting(i -> i.listing().toString())
        .containsExactly("FR0000120073@XPAR");
    assertThat(data.ignored()).isEqualTo(1);
    assertThat(client.isEnabled()).isTrue();
    server.verify();
  }

  @Test
  void keyIsFullyEncodedInTheQueryString() {
    RestClient.Builder other = RestClient.builder();
    MockRestServiceServer otherServer = MockRestServiceServer.bindTo(other).build();
    String special = "a+b&c=d";
    otherServer
        .expect(requestTo(Matchers.endsWith("api_token=a%2Bb%26c%3Dd")))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    EodhdClient.create(other, BASE_URL, special, USER_AGENT).referenceData();

    otherServer.verify();
  }

  @Test
  void dailyClosesUseTheRawCloseNeverTheAdjustedOne() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/eod/AI.PA")))
        .andExpect(queryParam("from", "2025-10-20"))
        .andExpect(queryParam("to", "2026-10-19"))
        .andExpect(queryParam("period", "d"))
        .andExpect(queryParam("api_token", key))
        .andRespond(
            withSuccess(
                """
                [{"date":"2025-10-20","open":171.0,"high":174.5,"low":170.2,"close":173.04,
                  "adjusted_close":154.0065,"volume":812345},
                 {"date":"2025-10-21","open":173.0,"high":175.0,"low":172.0,"close":174.1,
                  "adjusted_close":155.0,"volume":700000}]
                """,
                MediaType.APPLICATION_JSON));

    List<HistoricalClose> closes =
        client.dailyCloses(
            airLiquide, LocalDate.parse("2025-10-20"), LocalDate.parse("2026-10-19"));

    assertThat(closes)
        .containsExactly(
            new HistoricalClose(LocalDate.parse("2025-10-20"), new BigDecimal("173.04")),
            new HistoricalClose(LocalDate.parse("2025-10-21"), new BigDecimal("174.1")));
  }

  @Test
  void barsWithoutCloseAreSkipped() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/eod/AI.PA")))
        .andRespond(
            withSuccess(
                "[{\"date\":\"2025-10-20\",\"close\":null},{\"date\":null,\"close\":1.5}]",
                MediaType.APPLICATION_JSON));

    assertThat(client.dailyCloses(airLiquide, LocalDate.MIN, LocalDate.MAX)).isEmpty();
  }

  @Test
  void instrumentOutsideParisIsRejectedWithoutCall() {
    Instrument asml =
        new Instrument(
            InstrumentId.newId(),
            new Listing(new Isin("NL0010273215"), new Mic("XAMS")),
            "ASML",
            "ASML",
            InstrumentType.STOCK,
            Currency.getInstance("EUR"),
            null,
            false);

    assertThat(catchThrowable(() -> client.dailyCloses(asml, LocalDate.MIN, LocalDate.MAX)))
        .isInstanceOf(MarketDataUnavailableException.class)
        .hasMessageContaining("unsupported venue");
    server.verify();
  }

  @Test
  void networkErrorNeverExposesTheKey() {
    // The JDK client may put the full URL, query string included, in its exception messages.
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL.toString())))
        .andRespond(
            withException(
                new IOException("Connection reset: " + BASE_URL + "/eod/AI.PA?api_token=" + key)));

    Throwable failure = catchThrowable(client::referenceData);

    assertThat(failure)
        .isInstanceOf(MarketDataUnavailableException.class)
        .hasMessageContaining("ResourceAccessException");
    assertKeyAbsent(failure);
  }

  @Test
  void httpErrorNeverExposesTheKeyNorTheBody() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL.toString())))
        .andRespond(
            withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Invalid api_token " + key));

    Throwable failure = catchThrowable(client::referenceData);

    assertThat(failure)
        .isInstanceOf(MarketDataUnavailableException.class)
        .hasMessageContaining("HTTP 401");
    assertKeyAbsent(failure);
  }

  @Test
  void unreadableResponseNeverExposesTheKey() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL.toString())))
        .andRespond(withSuccess("<html>" + key + "</html>", MediaType.APPLICATION_JSON));

    Throwable failure =
        catchThrowable(() -> client.dailyCloses(airLiquide, LocalDate.MIN, LocalDate.MAX));

    assertThat(failure).isInstanceOf(MarketDataUnavailableException.class);
    assertKeyAbsent(failure);
  }

  @Test
  void emptyBodyIsAFailure() {
    server.expect(requestTo(Matchers.startsWith(BASE_URL.toString()))).andRespond(withSuccess());

    assertThat(catchThrowable(client::referenceData))
        .isInstanceOf(MarketDataUnavailableException.class)
        .hasMessageContaining("empty response");
  }

  /** Message, causes and stack trace as a log appender would print them. */
  private void assertKeyAbsent(Throwable failure) {
    var printed = new StringWriter();
    failure.printStackTrace(new PrintWriter(printed));
    assertThat(printed.toString()).doesNotContain(key).doesNotContain("api_token");
  }
}
