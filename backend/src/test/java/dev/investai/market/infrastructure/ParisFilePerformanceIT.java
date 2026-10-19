package dev.investai.market.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.MarketTestData;
import dev.investai.market.application.DailyCollection;
import dev.investai.market.application.PriceRecorder;
import dev.investai.market.domain.CollectionJournal;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.PriceSource;
import dev.investai.market.domain.SessionCloseCalculator;
import dev.investai.market.domain.TradeFileSegment;
import dev.investai.support.IntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Collection of a Paris equities file of realistic size for the whole reference data, on the real
 * database (spec 2.2, criterion 12): ~483,000 trades, ~1,300 listings, unknown ISINs. Checks the
 * result and logs the durations; the ceiling only catches a gross regression (e.g. quadratic).
 */
@IntegrationTest
class ParisFilePerformanceIT {

  private static final Logger LOG = LoggerFactory.getLogger(ParisFilePerformanceIT.class);

  private static final Instant MONDAY_MORNING = Instant.parse("2026-10-19T08:00:00Z");
  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final int REGULATED = 1_050;
  private static final int GROWTH = 250;
  private static final int UNKNOWN = 200;
  private static final int TRADES = 483_000;
  private static final long MAX_INFLATED_BYTES = 500L * 1024 * 1024;
  private static final Duration CEILING = Duration.ofMinutes(2);
  private static final String SEGMENT = TradeFileSegment.EQUITIES.name();

  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private CollectionJournal journal;
  @Autowired private PriceRecorder recorder;
  @Autowired private DailyPriceRepository prices;

  private final MifirFileReader reader = new MifirFileReader(MAX_INFLATED_BYTES);

  @Test
  void realisticParisFileIsCollectedForTheWholeReferenceDataWithinBounds() {
    MarketTestData.clearTables(jdbc);
    SyntheticParisFile file = SyntheticParisFile.generate(REGULATED, GROWTH, UNKNOWN, TRADES);
    instruments.upsertReference(file.referenceData(), MONDAY_MORNING);
    // Only the equities file is pending: the ETF segment is marked as collected.
    journal.recordFile(FRIDAY, TradeFileSegment.ETFS_FUNDS, 0, MONDAY_MORNING);

    var calculator = new SessionCloseCalculator(instruments.findAll(), FRIDAY);
    long start = System.nanoTime();
    var summary = reader.read(SEGMENT, file.open(), calculator::accept);
    var result = calculator.result();
    Duration readAndCompute = since(start);

    var collection =
        new DailyCollection(
            (segment, trades) -> reader.read(segment.name(), file.open(), trades),
            instruments,
            journal,
            recorder,
            Clock.fixed(MONDAY_MORNING, ZoneOffset.UTC));
    start = System.nanoTime();
    var outcome = collection.collect();
    Duration collect = since(start);

    start = System.nanoTime();
    recorder.recordSessionFile(FRIDAY, TradeFileSegment.EQUITIES, result.closes());
    Duration rewrite = since(start);

    report(file, readAndCompute, collect, rewrite);
    assertThat(summary.trades()).isEqualTo(TRADES);
    assertThat(summary.rejected()).isZero();
    assertThat(file.inflatedBytes()).as("~85 MB in the real file").isGreaterThan(75L * 1024 * 1024);
    assertThat(result.unknownIsins()).isEqualTo(UNKNOWN);
    assertThat(result.notStorable()).isEmpty();
    assertThat(result.ambiguousTimestamps()).isEmpty();
    assertThat(result.tradedElsewhereOnly()).isEmpty();
    assertThat(result.closes()).hasSize(REGULATED + GROWTH);
    assertThat(outcome).isEqualTo(DailyCollection.Outcome.COLLECTED);
    assertThat(prices.count(FRIDAY, PriceSource.EURONEXT)).isEqualTo(REGULATED + GROWTH);
    var listings =
        instruments.findAll().stream()
            .collect(
                Collectors.toMap(
                    instrument -> instrument.id(), instrument -> instrument.listing()));
    assertThat(result.closes())
        .allSatisfy(
            close -> {
              var listing = listings.get(close.instrumentId());
              assertThat(close.volume()).isEqualTo(file.volumes().get(listing));
              assertThat(close.tradeCount()).isEqualTo(file.tradeCounts().get(listing));
            });
    assertThat(storedTotals())
        .containsExactly(sum(file.volumes().values()), sum(file.tradeCounts().values()));
    assertThat(collect).isLessThan(CEILING);
  }

  private long[] storedTotals() {
    return jdbc.sql(
            "SELECT sum(volume), sum(trade_count) FROM daily_price WHERE trading_date = :day")
        .param("day", FRIDAY)
        .query((rs, row) -> new long[] {rs.getLong(1), rs.getLong(2)})
        .single();
  }

  private static long sum(Collection<? extends Number> values) {
    return values.stream().mapToLong(Number::longValue).sum();
  }

  private static Duration since(long start) {
    return Duration.ofNanos(System.nanoTime() - start);
  }

  private static void report(
      SyntheticParisFile file, Duration readAndCompute, Duration collect, Duration rewrite) {
    long trades = TRADES;
    long listings = REGULATED + GROWTH;
    long inflatedMb = file.inflatedBytes() / (1024 * 1024);
    long compressedMb = file.compressedBytes() / (1024 * 1024);
    long readMs = readAndCompute.toMillis();
    long collectMs = collect.toMillis();
    long rewriteMs = rewrite.toMillis();
    LOG.info(
        "Paris file performance: trades={}, listings={}, inflatedMb={}, compressedMb={},"
            + " readAndComputeMs={}, collectMs={}, rewriteMs={}",
        trades,
        listings,
        inflatedMb,
        compressedMb,
        readMs,
        collectMs,
        rewriteMs);
  }
}
