package dev.investai.market.application;

import static dev.investai.market.MarketTestData.isin;
import static dev.investai.market.MarketTestData.listed;
import static dev.investai.market.application.FakeProviders.trade;
import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.MarketTestData;
import dev.investai.market.domain.CatchUpQueue;
import dev.investai.market.domain.CatchUpQueue.CatchUpRequest;
import dev.investai.market.domain.CollectionJournal;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.PriceSource;
import dev.investai.market.domain.TradeFileSegment;
import dev.investai.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Daily Euronext collection on the real database, with a fake file source (spec 2.1, criteria 9 to
 * 13). Monday 19 October 2026, 10:00 in Paris: the expected session is Friday 16.
 */
@IntegrationTest
class DailyCollectionIT {

  private static final Instant MONDAY_MORNING = Instant.parse("2026-10-19T08:00:00Z");
  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final LocalDate THURSDAY = LocalDate.parse("2026-10-15");
  private static final LocalDate WEDNESDAY = LocalDate.parse("2026-10-14");
  private static final Isin AI = isin("FR000012007");
  private static final Isin CW8 = isin("LU168104359");
  private static final Isin TTE = isin("FR000012027");
  private static final String AI_FRIDAY_CLOSE_TIME = "2026-10-16T15:35:24Z";

  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;
  @Autowired private CollectionJournal journal;
  @Autowired private CatchUpQueue queue;
  @Autowired private PriceRecorder recorder;

  private final FakeTradeFiles files = new FakeTradeFiles();
  private final MutableClock clock = new MutableClock(MONDAY_MORNING);
  private DailyCollection collection;
  private Instrument airLiquide;
  private Instrument msciWorld;

  @BeforeEach
  void setUp() {
    MarketTestData.clearTables(jdbc);
    collection = new DailyCollection(files, instruments, journal, recorder, clock);
    instruments.upsertReference(
        List.of(
            listed(AI, "AI", "Air Liquide"),
            listed(CW8, "CW8", "Amundi MSCI World"),
            listed(TTE, "TTE", "TotalEnergies")),
        MONDAY_MORNING);
    airLiquide = tracked(AI);
    msciWorld = tracked(CW8);
    files.publish(
        TradeFileSegment.EQUITIES,
        trade(AI, AI_FRIDAY_CLOSE_TIME, "173.1600000"),
        trade(AI, "2026-10-16T15:00:00Z", "172.0000000"));
    files.publish(TradeFileSegment.ETFS_FUNDS, trade(CW8, "2026-10-16T15:35:12Z", "709.5500000"));
  }

  @Test
  void collectsThePreviousSessionOnceThenDownloadsNothing() {
    assertThat(collection.collect()).isEqualTo(DailyCollection.Outcome.COLLECTED);
    assertThat(collection.collect()).isEqualTo(DailyCollection.Outcome.ALREADY_COLLECTED);

    assertThat(files.downloads())
        .containsExactly(TradeFileSegment.EQUITIES, TradeFileSegment.ETFS_FUNDS);
    assertThat(prices.findLatest(airLiquide.id()))
        .hasValueSatisfying(
            close -> {
              assertThat(close.tradingDate()).isEqualTo(FRIDAY);
              assertThat(close.close()).isEqualByComparingTo("173.16");
              assertThat(close.source()).isEqualTo(PriceSource.EURONEXT);
            });
    assertThat(prices.findLatest(msciWorld.id())).isPresent();
    assertThat(journal.isCollected(FRIDAY, TradeFileSegment.EQUITIES)).isTrue();
    assertThat(journal.isCollected(FRIDAY, TradeFileSegment.ETFS_FUNDS)).isTrue();
  }

  @Test
  void rewritingACollectedSessionCreatesNoDuplicate() {
    collection.collect();
    jdbc.sql("DELETE FROM market_collection").update();

    collection.collect();

    assertThat(prices.findBetween(airLiquide.id(), FRIDAY, FRIDAY)).hasSize(1);
    assertThat(jdbc.sql("SELECT count(*) FROM market_collection").query(Integer.class).single())
        .isEqualTo(2);
  }

  @Test
  void fileOfAnotherSessionWritesNothingAndIsRetriedLater() {
    files.publish(TradeFileSegment.EQUITIES, trade(AI, "2026-10-15T15:35:00Z", "170.0000000"));

    assertThat(collection.collect()).isEqualTo(DailyCollection.Outcome.NOT_YET_AVAILABLE);
    assertThat(prices.findLatest(airLiquide.id())).isEmpty();
    assertThat(journal.isCollected(FRIDAY, TradeFileSegment.EQUITIES)).isFalse();

    files.publish(TradeFileSegment.EQUITIES, trade(AI, AI_FRIDAY_CLOSE_TIME, "173.1600000"));
    assertThat(collection.collect()).isEqualTo(DailyCollection.Outcome.COLLECTED);
  }

  @Test
  void failedDownloadWritesNothing() {
    files.failing(true);

    assertThat(collection.collect()).isEqualTo(DailyCollection.Outcome.FAILED);
    assertThat(prices.findLatest(airLiquide.id())).isEmpty();
    assertThat(files.downloads()).containsExactly(TradeFileSegment.EQUITIES);
  }

  @Test
  void everyInstrumentOfTheReferenceDataGetsItsCloseWithVolumeAndTradeCount() {
    // TotalEnergies is in the reference data but nobody follows it.
    files.publish(
        TradeFileSegment.EQUITIES,
        trade(AI, AI_FRIDAY_CLOSE_TIME, "173.1600000"),
        trade(AI, "2026-10-16T15:00:00Z", "172.0000000"),
        trade(TTE, "2026-10-16T15:35:10Z", "55.1000000"));
    Instrument total = instruments.search(TTE.value(), 1).getFirst();

    collection.collect();

    assertThat(total.tracked()).isFalse();
    assertThat(prices.findLatest(total.id()))
        .hasValueSatisfying(
            close -> {
              assertThat(close.tradingDate()).isEqualTo(FRIDAY);
              assertThat(close.close()).isEqualByComparingTo("55.10");
              assertThat(close.source()).isEqualTo(PriceSource.EURONEXT);
              assertThat(close.volume()).isEqualTo(10L);
              assertThat(close.tradeCount()).isEqualTo(1);
            });
    assertThat(prices.findLatest(airLiquide.id()))
        .hasValueSatisfying(
            close -> {
              assertThat(close.volume()).isEqualTo(20L);
              assertThat(close.tradeCount()).isEqualTo(2);
            });
    assertThat(closesCount("EQUITIES")).isEqualTo(2);
  }

  @Test
  void instrumentsOutsideTheReferenceDataAndDaysWithoutTradeGetNoClose() {
    // The ETF file only has trades of an instrument missing from the reference data.
    files.publish(
        TradeFileSegment.ETFS_FUNDS, trade(isin("FR001052727"), "2026-10-16T15:00:00Z", "71.18"));

    collection.collect();

    assertThat(prices.findLatest(msciWorld.id())).isEmpty();
    assertThat(closesCount("ETFS_FUNDS")).isZero();
    assertThat(jdbc.sql("SELECT count(*) FROM instrument").query(Integer.class).single())
        .as("an unknown ISIN never creates an instrument")
        .isEqualTo(3);
  }

  @Test
  void missedSessionsAreQueuedForCatchUpAfterASuccessfulCollection() {
    journal.recordFile(WEDNESDAY, TradeFileSegment.EQUITIES, 1, MONDAY_MORNING);
    journal.recordFile(WEDNESDAY, TradeFileSegment.ETFS_FUNDS, 1, MONDAY_MORNING);
    instruments.markHistoryLoaded(airLiquide.id(), MONDAY_MORNING);

    collection.collect();

    assertThat(queue.pending())
        .as("only instruments with history: the others get a full year of history")
        .containsExactly(new CatchUpRequest(airLiquide.id(), THURSDAY, THURSDAY));
    assertThat(journal.isCollected(THURSDAY, TradeFileSegment.ETFS_FUNDS)).isTrue();
    assertThat(
            jdbc.sql("SELECT source FROM market_collection WHERE trading_date = :d")
                .param("d", THURSDAY)
                .query(String.class)
                .single())
        .isEqualTo("EODHD");
  }

  @Test
  void sessionWithASingleCollectedFileIsCaughtUpToo() {
    // Wednesday: the ETF file never came (application stopped at 10:00), Thursday: nothing.
    journal.recordFile(WEDNESDAY, TradeFileSegment.EQUITIES, 1, MONDAY_MORNING);
    instruments.markHistoryLoaded(airLiquide.id(), MONDAY_MORNING);

    collection.collect();

    assertThat(queue.pending())
        .containsExactly(new CatchUpRequest(airLiquide.id(), WEDNESDAY, THURSDAY));
  }

  @Test
  void catchUpIsScheduledEvenWhenTheProcessStoppedRightAfterTheCollection() {
    journal.recordFile(WEDNESDAY, TradeFileSegment.EQUITIES, 1, MONDAY_MORNING);
    journal.recordFile(WEDNESDAY, TradeFileSegment.ETFS_FUNDS, 1, MONDAY_MORNING);
    journal.recordFile(FRIDAY, TradeFileSegment.EQUITIES, 1, MONDAY_MORNING);
    journal.recordFile(FRIDAY, TradeFileSegment.ETFS_FUNDS, 1, MONDAY_MORNING);
    instruments.markHistoryLoaded(airLiquide.id(), MONDAY_MORNING);

    assertThat(collection.collect()).isEqualTo(DailyCollection.Outcome.ALREADY_COLLECTED);

    assertThat(queue.pending())
        .containsExactly(new CatchUpRequest(airLiquide.id(), THURSDAY, THURSDAY));
    assertThat(files.downloads()).isEmpty();
  }

  @Test
  void firstCollectionEverHasNothingToCatchUp() {
    instruments.markHistoryLoaded(airLiquide.id(), MONDAY_MORNING);

    collection.collect();

    assertThat(queue.pending()).isEmpty();
  }

  @Test
  void euronextCloseReplacesAnEodhdCloseOfTheSameSession() {
    recorder.recordHistory(
        airLiquide.id(),
        List.of(
            new DailyClose(
                airLiquide.id(),
                FRIDAY,
                new BigDecimal("173.10"),
                MarketTestData.EUR,
                PriceSource.EODHD)));

    collection.collect();

    assertThat(prices.findLatest(airLiquide.id()))
        .hasValueSatisfying(close -> assertThat(close.source()).isEqualTo(PriceSource.EURONEXT));
  }

  private int closesCount(String segment) {
    return jdbc.sql("SELECT closes_count FROM market_collection WHERE segment = :segment")
        .param("segment", segment)
        .query(Integer.class)
        .single();
  }

  private Instrument tracked(Isin isin) {
    Instrument instrument = instruments.search(isin.value(), 1).stream().findFirst().orElseThrow();
    instruments.markTracked(instrument.id(), MONDAY_MORNING);
    return instruments.findById(instrument.id()).orElseThrow();
  }
}
