package dev.investai.market.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.IndicatorFixtures;
import dev.investai.market.MarketTestData;
import dev.investai.market.domain.CollectionJournal;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.EuronextCalendar;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.IndicatorWindow;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.ScreeningCriteria;
import dev.investai.market.domain.ScreeningSort;
import dev.investai.market.domain.YearWindow;
import dev.investai.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Daily computation for the whole reference data on the real database (spec 2.4, criterion 6):
 * 1,300 stocks and the two benchmarks × 253 sessions of synthetic closes. Logs the durations; the
 * ceiling only catches a gross regression. Results recorded in the spec.
 */
@IntegrationTest
class IndicatorPerformanceIT {

  private static final Logger LOG = LoggerFactory.getLogger(IndicatorPerformanceIT.class);

  private static final Instant MONDAY_MORNING = Instant.parse("2026-10-19T08:00:00Z");
  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final int STOCKS = 1_300;
  private static final int INSTRUMENTS = STOCKS + 2;
  private static final Duration CEILING = Duration.ofMinutes(2);

  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;
  @Autowired private IndicatorRepository indicators;
  @Autowired private CollectionJournal journal;
  @Autowired private IndicatorSettings settings;

  private final Clock clock = Clock.fixed(MONDAY_MORNING, ZoneOffset.UTC);

  @AfterEach
  void clear() {
    // ~330,000 closes: never left to the next integration test.
    MarketTestData.clearTables(jdbc);
  }

  @Test
  void wholeReferenceDataIsComputedWithinBoundsAndScreenedFromTheTable() {
    MarketTestData.clearTables(jdbc);
    var window = YearWindow.ending(FRIDAY, new EuronextCalendar());
    instruments.upsertReference(IndicatorFixtures.stocks(STOCKS), MONDAY_MORNING);
    IndicatorFixtures.instrument(
        instruments, settings.msciWorld(), InstrumentType.ETF, "MSCI World (test)", MONDAY_MORNING);
    IndicatorFixtures.instrument(
        instruments, settings.cac40(), InstrumentType.ETF, "CAC 40 (test)", MONDAY_MORNING);
    long start = System.nanoTime();
    int closes =
        IndicatorFixtures.insertSyntheticYear(
            jdbc, window, MONDAY_MORNING.minus(Duration.ofHours(1)));
    var insert = since(start);

    var computation =
        new IndicatorComputation(instruments, prices, journal, indicators, settings, clock);
    start = System.nanoTime();
    var outcome = computation.refresh();
    var compute = since(start);

    var queries = new IndicatorQueries(instruments, prices, journal, indicators, settings, clock);
    start = System.nanoTime();
    var screening =
        queries.screen(
            new ScreeningCriteria(
                null,
                new BigDecimal("1000"),
                null,
                IndicatorWindow.ONE_YEAR,
                null,
                ScreeningSort.VOLATILITY,
                ScreeningCriteria.MAX_LIMIT));
    var screen = since(start);

    long insertMillis = insert.toMillis();
    long computeMillis = compute.toMillis();
    long screenMillis = screen.toMillis();
    LOG.info(
        "Indicators of {} instruments ({} closes): insert={} ms, compute={} ms, screen={} ms",
        INSTRUMENTS,
        closes,
        insertMillis,
        computeMillis,
        screenMillis);
    assertThat(outcome).isEqualTo(IndicatorComputation.Outcome.COMPUTED);
    assertThat(indicators.findAll())
        .hasSize(INSTRUMENTS)
        .allSatisfy(stored -> assertThat(stored.indicators().volatility().available()).isTrue());
    assertThat(screening.matchCount()).isEqualTo(INSTRUMENTS);
    assertThat(screening.results()).hasSize(ScreeningCriteria.MAX_LIMIT);
    assertThat(compute.plus(screen)).isLessThan(CEILING);
  }

  private static Duration since(long start) {
    return Duration.ofNanos(System.nanoTime() - start);
  }
}
