package dev.investai.market.api;

import static dev.investai.market.IndicatorFixtures.instrument;
import static dev.investai.market.IndicatorFixtures.saveYear;
import static dev.investai.market.MarketTestData.isin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.investai.market.MarketTestData;
import dev.investai.market.application.IndicatorComputation;
import dev.investai.market.application.IndicatorSettings;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.EuronextCalendar;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.YearWindow;
import dev.investai.support.IntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Indicators API on the real database and security (spec 2.4, criteria 6 and 9): the computation
 * runs once, then the endpoints only read the stored table.
 */
@IntegrationTest
class IndicatorApiIT {

  private static final Instant MONDAY_MORNING = Instant.parse("2026-10-19T08:00:00Z");
  private static final YearWindow WINDOW =
      YearWindow.ending(LocalDate.parse("2026-10-16"), new EuronextCalendar());
  private static final String CODE = "$.code";

  @Autowired private MockMvcTester mvc;
  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;
  @Autowired private IndicatorRepository indicators;
  @Autowired private IndicatorSettings settings;

  private Instrument calm;
  private Instrument risky;
  private Instrument unseen;

  @BeforeEach
  void setUp() {
    MarketTestData.clearTables(jdbc);
    var msciWorld = etf(settings.msciWorld(), "MSCI World (test)");
    var cac40 = etf(settings.cac40(), "CAC 40 (test)");
    calm = stock("FR000012007", "Calme");
    risky = stock("FR000012027", "Risquée");
    Instant before = MONDAY_MORNING.minusSeconds(3600);
    saveYear(prices, msciWorld.id(), WINDOW, i -> i % 2 == 0 ? "100" : "101", 0, before);
    saveYear(prices, cac40.id(), WINDOW, i -> i % 2 == 0 ? "100" : "102", 0, before);
    saveYear(prices, calm.id(), WINDOW, i -> i % 2 == 0 ? "100" : "101", 0, before);
    saveYear(prices, risky.id(), WINDOW, i -> i % 2 == 0 ? "100" : "110", 0, before);
    new IndicatorComputation(
            instruments, prices, indicators, settings, Clock.fixed(MONDAY_MORNING, ZoneOffset.UTC))
        .refresh();
    // Added after the computation: no indicators yet.
    unseen = stock("FR000012031", "Nouvelle");
  }

  @Test
  void indicatorsOfAnInstrumentAreReadFromTheTable() {
    var response = get("/api/v1/market/instruments/" + risky.id() + "/indicators");

    assertThat(response).hasStatusOk();
    var json = assertThat(response).bodyJson();
    json.extractingPath("$.instrument.name").isEqualTo("Risquée");
    json.extractingPath("$.indicators.asOf").isEqualTo("2026-10-16");
    json.extractingPath("$.indicators.volatility.value").isEqualTo("151.83");
    json.extractingPath("$.indicators.msciWorld.correlation.value").isEqualTo("1.00");
    json.extractingPath("$.indicators.performanceOneYear.value").isEqualTo("0.00");
    json.extractingPath("$.computedAt").isEqualTo("2026-10-19T08:00:00Z");
  }

  @Test
  void anInstrumentNotComputedYetOrUnknownIsA404() {
    assertThat(get("/api/v1/market/instruments/" + unseen.id() + "/indicators"))
        .bodyJson()
        .extractingPath(CODE)
        .isEqualTo("indicators.not-computed");
    assertThat(get("/api/v1/market/instruments/" + UUID.randomUUID() + "/indicators"))
        .bodyJson()
        .extractingPath(CODE)
        .isEqualTo("instrument.not-found");
  }

  @Test
  void screeningFiltersEligibleLowVolatilityInstruments() {
    // Calm and the MSCI World ETF (100/101) have a volatility of 15.83 %, the CAC 40 ETF (100/102)
    // 31.6 %, risky (100/110) 151.83 %. French shares are eligible by the rule, the two ETFs as
    // benchmarks. Equal volatilities are ordered by name.
    var response =
        get("/api/v1/market/indicators?peaEligibility=ELIGIBLE&maxVolatility=25&sort=VOLATILITY");

    assertThat(response).hasStatusOk();
    var json = assertThat(response).bodyJson();
    json.extractingPath("$.matchCount").isEqualTo(2);
    json.extractingPath("$.results[*].instrument.name")
        .asArray()
        .containsExactly("Calme", "MSCI World (test)");
    json.extractingPath("$.results[0].indicators.volatility.value").isEqualTo("15.83");
  }

  @Test
  void screeningRejectsAnInvalidParameterWithItsName() {
    var response = get("/api/v1/market/indicators?limit=100");

    assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("validation.failed");
    assertThat(response).bodyJson().extractingPath("$.errors[0].field").isEqualTo("limit");
  }

  private MvcTestResult get(String uri) {
    return mvc.get().uri(uri).with(user("someone")).exchange();
  }

  private Instrument etf(Listing listing, String name) {
    return instrument(instruments, listing, InstrumentType.ETF, name, MONDAY_MORNING);
  }

  private Instrument stock(String isinBody, String name) {
    return instrument(
        instruments,
        new Listing(isin(isinBody), Mic.XPAR),
        InstrumentType.STOCK,
        name,
        MONDAY_MORNING);
  }
}
