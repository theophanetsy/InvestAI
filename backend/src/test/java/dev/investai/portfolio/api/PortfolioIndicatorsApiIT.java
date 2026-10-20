package dev.investai.portfolio.api;

import static dev.investai.market.IndicatorFixtures.instrument;
import static dev.investai.market.IndicatorFixtures.saveYear;
import static dev.investai.market.MarketTestData.isin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.investai.market.application.IndicatorSettings;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.EuronextCalendar;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.YearWindow;
import dev.investai.portfolio.PortfolioTestData;
import dev.investai.portfolio.domain.Cash;
import dev.investai.portfolio.domain.PortfolioLine;
import dev.investai.portfolio.domain.PortfolioLineId;
import dev.investai.portfolio.domain.PortfolioRepository;
import dev.investai.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Indicators of the declared PEA through the HTTP API, on the real database (spec 2.4, criteria 7
 * to 9): computed on demand from the stored closes. Synthetic closes of the year ending on Friday
 * 16 October 2026, the latest session before today.
 */
@IntegrationTest
class PortfolioIndicatorsApiIT {

  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");
  private static final YearWindow WINDOW =
      YearWindow.ending(LocalDate.parse("2026-10-16"), new EuronextCalendar());
  private static final String INDICATORS = "/api/v1/portfolio/indicators";

  @Autowired private MockMvcTester mvc;
  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;
  @Autowired private PortfolioRepository portfolio;
  @Autowired private IndicatorSettings settings;

  @BeforeEach
  void setUp() {
    PortfolioTestData.clearTables(jdbc);
  }

  @Test
  void currentPortfolioIsReplayedOverTheYearAgainstBothBenchmarks() {
    var msciWorld = instrument(instruments, settings.msciWorld(), InstrumentType.ETF, "CW8", NOW);
    var cac40 = instrument(instruments, settings.cac40(), InstrumentType.ETF, "CAC", NOW);
    var alternating = stock("FR000012007", "Alternée");
    var recent = stock("FR000012027", "Récente");
    saveYear(prices, msciWorld.id(), WINDOW, i -> i % 2 == 0 ? "100" : "105", 0, NOW);
    saveYear(prices, cac40.id(), WINDOW, i -> "100", 0, NOW);
    saveYear(prices, alternating.id(), WINDOW, i -> i % 2 == 0 ? "100" : "110", 0, NOW);
    saveYear(prices, recent.id(), WINDOW, i -> "20", YearWindow.SESSIONS - 10, NOW);
    portfolio.insert(line(alternating, 1));
    portfolio.insert(line(recent, 5));
    portfolio.saveCash(new Cash(new BigDecimal("100.00"), NOW));

    var response = get();

    assertThat(response).hasStatusOk();
    var json = assertThat(response).bodyJson();
    json.extractingPath("$.asOf").isEqualTo("2026-10-16");
    json.extractingPath("$.total").isEqualTo("300.00");
    json.extractingPath("$.composition.lineCount").isEqualTo(2);
    json.extractingPath("$.composition.cashPercent.value").isEqualTo("33.33");
    json.extractingPath("$.composition.stockPercent.value").isEqualTo("66.67");
    json.extractingPath("$.volatility.value").as("the alternating line alone").isEqualTo("151.83");
    json.extractingPath("$.lines[0].instrument.name").isEqualTo("Alternée");
    json.doesNotHavePath("$.lines[0].exclusion");
    json.extractingPath("$.lines[0].indicators.msciWorld.beta.value").isEqualTo("1.96");
    json.extractingPath("$.lines[1].exclusion").isEqualTo("INSUFFICIENT_HISTORY");
    json.extractingPath("$.lines[1].indicators.closesOneYear").isEqualTo(10);
    json.extractingPath("$.curve").asArray().hasSize(YearWindow.SESSIONS);
    json.extractingPath("$.curve[1].portfolio").isEqualTo("110.00");
    json.extractingPath("$.curve[1].msciWorld").isEqualTo("105.00");
    json.extractingPath("$.curve[1].cac40").isEqualTo("100.00");
    json.extractingPath("$.correlations.rows[0][0].value").isEqualTo("1.00");
  }

  @Test
  void withoutAnyCloseEverythingIsExplicitlyUnavailable() {
    var unpriced = stock("FR000012031", "Sans cours");
    portfolio.insert(line(unpriced, 3));

    var response = get();

    assertThat(response).hasStatusOk();
    var json = assertThat(response).bodyJson();
    json.doesNotHavePath("$.asOf");
    json.extractingPath("$.lines[0].exclusion").isEqualTo("NOT_VALUED");
    json.doesNotHavePath("$.lines[0].indicators");
    json.extractingPath("$.volatility.status").isEqualTo("NOT_COMPUTABLE");
    json.extractingPath("$.curve").asArray().isEmpty();
  }

  private MvcTestResult get() {
    return mvc.get().uri(INDICATORS).with(user("someone")).exchange();
  }

  private Instrument stock(String isinBody, String name) {
    return instrument(
        instruments, new Listing(isin(isinBody), Mic.XPAR), InstrumentType.STOCK, name, NOW);
  }

  private static PortfolioLine line(Instrument instrument, long quantity) {
    return new PortfolioLine(PortfolioLineId.newId(), instrument.id(), quantity, null, NOW);
  }
}
