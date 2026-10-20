package dev.investai.market.application;

import static dev.investai.market.IndicatorFixtures.close;
import static dev.investai.market.IndicatorFixtures.instrument;
import static dev.investai.market.IndicatorFixtures.saveYear;
import static dev.investai.market.MarketTestData.isin;
import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.MarketTestData;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.EuronextCalendar;
import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.IndicatorWindow;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.StoredIndicators;
import dev.investai.market.domain.YearWindow;
import dev.investai.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Daily computation of the indicators on the real database (spec 2.4, criterion 6). Monday 19
 * October 2026, 10:00 in Paris: the latest session is Friday 16. Synthetic closes only; expected
 * values derived in {@code IndicatorCalculatorTest}.
 */
@IntegrationTest
class IndicatorComputationIT {

  private static final Instant MONDAY_MORNING = Instant.parse("2026-10-19T08:00:00Z");
  private static final Instant AN_HOUR_BEFORE = MONDAY_MORNING.minus(Duration.ofHours(1));
  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final LocalDate MONDAY = LocalDate.parse("2026-10-19");
  private static final YearWindow WINDOW = YearWindow.ending(FRIDAY, new EuronextCalendar());

  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;
  @Autowired private IndicatorRepository indicators;
  @Autowired private IndicatorSettings settings;

  private final MutableClock clock = new MutableClock(MONDAY_MORNING);
  private IndicatorComputation computation;
  private Instrument alternating;
  private Instrument recent;
  private Instrument msciWorld;
  private Instrument cac40;

  @BeforeEach
  void setUp() {
    MarketTestData.clearTables(jdbc);
    computation = new IndicatorComputation(instruments, prices, indicators, settings, clock);
    msciWorld = etf(settings.msciWorld(), "MSCI World (test)");
    cac40 = etf(settings.cac40(), "CAC 40 (test)");
    alternating = stock("FR000012007", "Alternée");
    recent = stock("FR000012027", "Récente");
  }

  private void loadYear() {
    // Same series as IndicatorCalculatorTest: 100/110, MSCI World 100/105, CAC 40 105/100.
    saveYear(prices, alternating.id(), WINDOW, i -> i % 2 == 0 ? "100" : "110", 0, AN_HOUR_BEFORE);
    saveYear(prices, msciWorld.id(), WINDOW, i -> i % 2 == 0 ? "100" : "105", 0, AN_HOUR_BEFORE);
    saveYear(prices, cac40.id(), WINDOW, i -> i % 2 == 0 ? "105" : "100", 0, AN_HOUR_BEFORE);
    saveYear(prices, recent.id(), WINDOW, i -> "20", YearWindow.SESSIONS - 10, AN_HOUR_BEFORE);
  }

  @Test
  void everyInstrumentGetsItsIndicatorsAndARunWithoutNewCloseComputesNothing() {
    loadYear();

    assertThat(computation.refresh()).isEqualTo(IndicatorComputation.Outcome.COMPUTED);

    var stored = stored(alternating);
    assertThat(stored.computedAt()).isEqualTo(MONDAY_MORNING);
    var set = stored.indicators();
    assertThat(set.asOf()).isEqualTo(FRIDAY);
    assertThat(set.closesOneYear()).isEqualTo(253);
    assertThat(value(set.volatility())).isEqualTo("151.83");
    assertThat(value(set.maxDrawdown())).as("110 to 100").isEqualTo("-9.09");
    assertThat(value(set.msciWorld().correlation())).isEqualTo("1.00");
    assertThat(value(set.msciWorld().beta())).isEqualTo("1.96");
    assertThat(value(set.cac40().correlation())).isEqualTo("-1.00");
    assertThat(set.averageTradedAmount().available()).isTrue();
    var young = stored(recent).indicators();
    assertThat(young.closesOneYear()).isEqualTo(10);
    assertThat(young.performance(IndicatorWindow.ONE_MONTH))
        .isEqualTo(Indicator.INSUFFICIENT_HISTORY);
    assertThat(young.msciWorld().beta()).isEqualTo(Indicator.INSUFFICIENT_HISTORY);
    assertThat(value(stored(msciWorld).indicators().msciWorld().beta()))
        .as("the benchmark against itself")
        .isEqualTo("1.00");
    assertThat(indicators.findAll()).hasSize(4);

    clock.set(MONDAY_MORNING.plus(Duration.ofHours(1)));
    assertThat(computation.refresh()).isEqualTo(IndicatorComputation.Outcome.UP_TO_DATE);
    assertThat(stored(alternating).computedAt()).isEqualTo(MONDAY_MORNING);
  }

  @Test
  void aNewCloseMakesTheNextRunComputeAgainWithTheSameResult() {
    loadYear();
    computation.refresh();
    var before = stored(alternating).indicators();

    clock.set(MONDAY_MORNING.plus(Duration.ofHours(1)));
    // A close of the recent instrument caught up after the run.
    prices.save(List.of(close(recent.id(), WINDOW.sessions().get(200), "20")), clock.instant());

    assertThat(computation.refresh()).isEqualTo(IndicatorComputation.Outcome.COMPUTED);
    assertThat(stored(alternating).indicators()).as("idempotent").isEqualTo(before);
    assertThat(stored(recent).indicators().closesOneYear()).isEqualTo(11);
    assertThat(stored(recent).computedAt()).isEqualTo(clock.instant());
  }

  @Test
  void aCloseOfTodayWaitsForTheNextDay() {
    loadYear();
    // EODHD may publish the close of the day in the evening, before the Euronext file.
    prices.save(List.of(close(alternating.id(), MONDAY, "1000")), MONDAY_MORNING);
    computation.refresh();
    assertThat(stored(alternating).indicators().asOf()).isEqualTo(FRIDAY);

    clock.set(Instant.parse("2026-10-20T08:00:00Z"));

    assertThat(computation.refresh()).isEqualTo(IndicatorComputation.Outcome.COMPUTED);
    var tuesday = stored(alternating).indicators();
    assertThat(tuesday.asOf()).isEqualTo(MONDAY);
    assertThat(value(tuesday.performance(IndicatorWindow.ONE_MONTH)))
        .as("1000 / 100 - 1: the reference session, 21 sessions before Monday, closed at 100")
        .isEqualTo("900.00");
  }

  @Test
  void nothingIsComputedWithoutCloses() {
    assertThat(computation.refresh()).isEqualTo(IndicatorComputation.Outcome.NO_CLOSES);
    assertThat(indicators.findAll()).isEmpty();
    assertThat(indicators.lastRun()).isEmpty();
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

  private StoredIndicators stored(Instrument instrument) {
    return indicators.find(instrument.id()).orElseThrow();
  }

  private static BigDecimal value(Indicator indicator) {
    return Objects.requireNonNull(indicator.value());
  }
}
