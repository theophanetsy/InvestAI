package dev.investai.portfolio.api;

import static dev.investai.market.MarketTestData.EUR;
import static dev.investai.market.MarketTestData.isin;
import static dev.investai.market.MarketTestData.listed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.jayway.jsonpath.JsonPath;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.ListedInstrument;
import dev.investai.market.domain.PriceSource;
import dev.investai.portfolio.PortfolioTestData;
import dev.investai.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Profile and declared portfolio through the HTTP API, on the real database and security (spec 2.3,
 * criteria 1 to 6 and 9).
 */
@IntegrationTest
class PortfolioApiIT {

  private static final String PROFILE = "/api/v1/profile";
  private static final String PORTFOLIO = "/api/v1/portfolio";
  private static final String LINES = "/api/v1/portfolio/lines";
  private static final String USER = "someone";
  private static final String CODE = "$.code";
  private static final String NO_AVERAGE_PRICE = "null";
  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

  @Autowired private MockMvcTester mvc;
  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;

  private Instrument airLiquide;
  private Instrument total;

  @BeforeEach
  void setUp() {
    PortfolioTestData.clearTables(jdbc);
    airLiquide = load(listed(isin("FR000012007"), "AI", "Air Liquide"));
    total = load(listed(isin("FR000012027"), "TTE", "TotalEnergies"));
    prices.save(
        List.of(close(airLiquide, FRIDAY.minusDays(1), "170"), close(airLiquide, FRIDAY, "173.16")),
        NOW);
  }

  @Test
  void profileIsNotFoundThenSavedIdempotentlyAndReadBack() {
    assertThat(get(PROFILE)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(get(PROFILE)).bodyJson().extractingPath(CODE).isEqualTo("profile.not-found");
    String body =
        """
        {"amountToInvest":"20000.5","horizonYears":10,"riskProfile":"BALANCED",
         "exclusions":["Tabac"],"objectives":"<img src=x onerror=alert(1)> retraite",
         "peaOpenedOn":"2019-03-01"}
        """;

    assertThat(send(HttpMethod.PUT, PROFILE, body)).hasStatusOk();
    assertThat(send(HttpMethod.PUT, PROFILE, body)).hasStatusOk();

    var profile = get(PROFILE);
    assertThat(profile).hasStatusOk();
    assertThat(profile).bodyJson().extractingPath("$.amountToInvest").isEqualTo("20000.50");
    assertThat(profile).bodyJson().extractingPath("$.currency").isEqualTo("EUR");
    assertThat(profile)
        .as("untrusted text stored and returned as typed")
        .bodyJson()
        .extractingPath("$.objectives")
        .isEqualTo("<img src=x onerror=alert(1)> retraite");
    assertThat(profile).bodyJson().extractingPath("$.peaOpenedOn").isEqualTo("2019-03-01");
    assertThat(jdbc.sql("SELECT count(*) FROM investor_profile").query(Integer.class).single())
        .isEqualTo(1);
  }

  @Test
  void addingALineFollowsTheInstrumentWithoutAnyExternalCall() {
    var added = addLine(airLiquide, "10", "\"150.1234\"");

    assertThat(added).hasStatus(HttpStatus.CREATED);
    assertThat(added).bodyJson().extractingPath("$.averagePrice").isEqualTo("150.1234");
    assertThat(instruments.findById(airLiquide.id()))
        .hasValueSatisfying(
            found -> {
              assertThat(found.tracked()).isTrue();
              assertThat(found.historyLoaded()).isFalse();
            });
  }

  @Test
  void portfolioIsValuedOnTheLastCloseWithCashAndFlagsLinesWithoutClose() {
    // Added in the reverse of the expected (alphabetical) order.
    addLine(total, "5", NO_AVERAGE_PRICE);
    addLine(airLiquide, "10", "\"150.1234\"");
    send(HttpMethod.PUT, PORTFOLIO + "/cash", "{\"amount\":\"268.40\"}");

    var portfolio = get(PORTFOLIO);

    assertThat(portfolio).hasStatusOk();
    var json = assertThat(portfolio).bodyJson();
    json.extractingPath("$.total").isEqualTo("2000.00");
    json.extractingPath("$.complete").isEqualTo(false);
    json.extractingPath("$.unpricedLines").isEqualTo(1);
    json.extractingPath("$.cash.amount").isEqualTo("268.40");
    json.extractingPath("$.cash.weightPercent").isEqualTo("13.42");
    json.extractingPath("$.lines[0].instrument.name").isEqualTo("Air Liquide");
    json.extractingPath("$.lines[0].instrument.peaEligibility.status").isEqualTo("ELIGIBLE");
    json.extractingPath("$.lines[0].lastClose.close").isEqualTo("173.1600");
    json.extractingPath("$.lines[0].lastClose.date").isEqualTo(FRIDAY.toString());
    json.extractingPath("$.lines[0].value").isEqualTo("1731.60");
    json.extractingPath("$.lines[0].unrealizedGain").isEqualTo("230.37");
    json.extractingPath("$.lines[0].weightPercent").isEqualTo("86.58");
    json.extractingPath("$.lines[1].instrument.name").isEqualTo("TotalEnergies");
    json.doesNotHavePath("$.lines[1].value");
    json.doesNotHavePath("$.lines[1].lastClose");
  }

  @Test
  void closeInAnotherCurrencyIsNeverMixedIntoTheTotalInEuros() {
    prices.save(
        List.of(
            new DailyClose(
                total.id(),
                FRIDAY,
                new BigDecimal("60"),
                Currency.getInstance("USD"),
                PriceSource.EODHD)),
        NOW);
    addLine(total, "5", NO_AVERAGE_PRICE);

    var portfolio = get(PORTFOLIO);

    assertThat(portfolio).bodyJson().extractingPath("$.total").isEqualTo("0.00");
    assertThat(portfolio).bodyJson().extractingPath("$.unpricedLines").isEqualTo(1);
    assertThat(portfolio).bodyJson().doesNotHavePath("$.lines[0].value");
  }

  @Test
  void emptyPortfolioIsWorthZero() {
    var portfolio = get(PORTFOLIO);

    assertThat(portfolio).hasStatusOk();
    assertThat(portfolio).bodyJson().extractingPath("$.lines.length()").isEqualTo(0);
    assertThat(portfolio).bodyJson().extractingPath("$.total").isEqualTo("0.00");
    assertThat(portfolio).bodyJson().doesNotHavePath("$.cash");
  }

  @Test
  void anInstrumentAppearsOnlyOnceAndMustBeKnownAndInEuros() {
    Instrument dollars =
        load(
            new ListedInstrument(
                listed(isin("IE00B4L5Y98"), "IWDA", "iShares World").listing(),
                "IWDA",
                "iShares World",
                InstrumentType.ETF,
                Currency.getInstance("USD")));
    addLine(airLiquide, "1", NO_AVERAGE_PRICE);

    var duplicate = addLine(airLiquide, "2", NO_AVERAGE_PRICE);
    var notInEuros = addLine(dollars, "2", NO_AVERAGE_PRICE);
    var unknown =
        send(
            HttpMethod.POST,
            LINES,
            "{\"instrumentId\":\"00000000-0000-0000-0000-000000000000\",\"quantity\":1}");

    assertThat(duplicate).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(duplicate)
        .bodyJson()
        .extractingPath(CODE)
        .isEqualTo("portfolio.line.duplicate-instrument");
    assertThat(notInEuros)
        .bodyJson()
        .extractingPath(CODE)
        .isEqualTo("portfolio.line.currency-not-supported");
    assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(unknown).bodyJson().extractingPath(CODE).isEqualTo("instrument.not-found");
  }

  @Test
  void lineIsUpdatedThenRemoved() throws UnsupportedEncodingException {
    String id = lineId(addLine(airLiquide, "10", "\"150\""));

    var updated = send(HttpMethod.PUT, LINES + "/" + id, "{\"quantity\":12}");
    assertThat(updated).hasStatusOk();
    assertThat(updated).bodyJson().extractingPath("$.quantity").isEqualTo(12);
    assertThat(updated).bodyJson().doesNotHavePath("$.averagePrice");

    assertThat(send(HttpMethod.DELETE, LINES + "/" + id, "")).hasStatus(HttpStatus.NO_CONTENT);
    var again = send(HttpMethod.DELETE, LINES + "/" + id, "");
    assertThat(again).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(instruments.findById(airLiquide.id()))
        .as("still followed after the removal of its line")
        .hasValueSatisfying(found -> assertThat(found.tracked()).isTrue());
    assertThat(again).bodyJson().extractingPath(CODE).isEqualTo("portfolio.line.not-found");
    assertThat(send(HttpMethod.PUT, LINES + "/" + id, "{\"quantity\":1}"))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  private MvcTestResult addLine(Instrument instrument, String quantity, String averagePrice) {
    return send(
        HttpMethod.POST,
        LINES,
        String.format(
            "{\"instrumentId\":\"%s\",\"quantity\":%s,\"averagePrice\":%s}",
            instrument.id(), quantity, averagePrice));
  }

  private static String lineId(MvcTestResult result) throws UnsupportedEncodingException {
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private MvcTestResult get(String uri) {
    return mvc.get().uri(uri).with(user(USER)).exchange();
  }

  /** Real CSRF exchange, like the browser (csrf() would break the shared filter, CLAUDE.md). */
  private MvcTestResult send(HttpMethod method, String uri, String body) {
    Cookie csrf =
        Objects.requireNonNull(
            mvc.get().uri("/api/v1/auth/me").exchange().getResponse().getCookie("XSRF-TOKEN"));
    return mvc.method(method)
        .uri(uri)
        .with(user(USER))
        .cookie(csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }

  private Instrument load(ListedInstrument entry) {
    instruments.upsertReference(List.of(entry), NOW);
    return instruments.findByListing(entry.listing()).orElseThrow();
  }

  private static DailyClose close(Instrument instrument, LocalDate day, String price) {
    return new DailyClose(instrument.id(), day, new BigDecimal(price), EUR, PriceSource.EURONEXT);
  }
}
