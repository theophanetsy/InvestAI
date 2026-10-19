package dev.investai.market.application;

import static dev.investai.market.MarketTestData.isin;
import static dev.investai.market.MarketTestData.listed;
import static dev.investai.market.application.FakeProviders.close;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.investai.market.MarketTestData;
import dev.investai.market.domain.ApiCallCounter;
import dev.investai.market.domain.CatchUpQueue;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.PriceSource;
import dev.investai.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * EODHD maintenance on the real database with a fake provider: catch-up first, then history, then
 * reference data, within the daily budget (spec 2.1, criteria 4, 7, 8, 13 and 16).
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class EodhdMaintenanceIT {

  private static final String REFERENCE_CALL = "exchange-symbol-list";
  private static final Instant MONDAY = Instant.parse("2026-10-19T08:00:00Z");
  private static final String THURSDAY_TEXT = "2026-10-15";
  private static final String AIR_LIQUIDE = "Air Liquide";
  private static final LocalDate THURSDAY = LocalDate.parse(THURSDAY_TEXT);
  private static final String FRIDAY_TEXT = "2026-10-16";
  private static final LocalDate FRIDAY = LocalDate.parse(FRIDAY_TEXT);
  private static final Isin AI = isin("FR000012007");
  private static final Isin TTE = isin("FR000012027");
  private static final Isin CW8 = isin("LU168104359");

  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;
  @Autowired private CatchUpQueue queue;
  @Autowired private ApiCallCounter usage;
  @Autowired private PriceRecorder recorder;

  private final FakeEodhd eodhd = new FakeEodhd();
  private final MutableClock clock = new MutableClock(MONDAY);

  @BeforeEach
  void clear() {
    MarketTestData.clearTables(jdbc);
  }

  @Test
  void firstStartLoadsReferenceDataThenTracksBenchmarksAndLoadsTheirHistory() {
    eodhd.reference(listed(AI, "AI", AIR_LIQUIDE), listed(CW8, "CW8", "Amundi MSCI World"));
    eodhd.history(CW8, close(THURSDAY_TEXT, "705.10"), close(FRIDAY_TEXT, "709.55"));

    maintenance(15).run();

    assertThat(eodhd.calls()).containsExactly(REFERENCE_CALL, "eod CW8 2025-10-19 2026-10-16");
    Instrument cw8 = find(CW8);
    assertThat(cw8.tracked()).isTrue();
    assertThat(cw8.historyLoaded()).isTrue();
    assertThat(find(AI).tracked()).isFalse();
    assertThat(prices.findBetween(cw8.id(), THURSDAY, FRIDAY))
        .extracting(DailyClose::source)
        .containsOnly(PriceSource.EODHD);
  }

  @Test
  void referenceDataIsRefreshedOnlyWhenOlderThanItsMaximumAge() {
    eodhd.reference(listed(AI, "AI", AIR_LIQUIDE));
    maintenance(15).run();

    clock.set(MONDAY.plus(Duration.ofDays(6)));
    maintenance(15).run();
    clock.set(MONDAY.plus(Duration.ofDays(8)));
    maintenance(15).run();

    assertThat(eodhd.calls()).containsExactly(REFERENCE_CALL, REFERENCE_CALL);
  }

  @Test
  void trackedInstrumentGetsOneYearOfRawClosesOnce() {
    Instrument ai = trackedWithoutHistory(AI, "AI");
    eodhd.history(AI, close("2025-10-20", "173.04"), close(FRIDAY_TEXT, "173.16"));

    maintenance(15).run();
    maintenance(15).run();

    assertThat(eodhd.calls()).containsExactly("eod AI 2025-10-19 2026-10-16");
    assertThat(prices.findBetween(ai.id(), LocalDate.parse("2025-10-19"), FRIDAY))
        .extracting(close -> close.close().toPlainString())
        .containsExactly("173.0400", "173.1600");
  }

  @Test
  void trackingAnInstrumentWithEuronextClosesLoadsItsHistoryAndKeepsThem() {
    instruments.upsertReference(List.of(listed(AI, "AI", AIR_LIQUIDE)), MONDAY);
    Instrument ai = find(AI);
    var official = FakeProviders.euronextClose(ai.id(), FRIDAY, "173.16", 812_345L, 4_321);
    prices.save(List.of(official), MONDAY);
    instruments.markTracked(ai.id(), MONDAY);
    eodhd.history(
        AI,
        close("2026-10-14", "172.00", "2.5"),
        close(THURSDAY_TEXT, "172.50", "700000"),
        close(FRIDAY_TEXT, "173.00", "1"));

    maintenance(15).run();

    assertThat(find(AI).historyLoaded()).isTrue();
    assertThat(prices.findBetween(ai.id(), THURSDAY.minusDays(1), FRIDAY))
        .as("a fractional EODHD volume is unknown, never rounded")
        .extracting(DailyClose::source, DailyClose::volume, DailyClose::tradeCount)
        .containsExactly(
            tuple(PriceSource.EODHD, null, null),
            tuple(PriceSource.EODHD, 700_000L, null),
            tuple(PriceSource.EURONEXT, 812_345L, 4_321));
    assertThat(prices.findLatest(ai.id())).hasValue(official);
  }

  @Test
  void catchUpComesFirstAndNeverReplacesEuronextCloses(CapturedOutput output) {
    Instrument ai = trackedWithHistory(AI, "AI");
    recorder.scheduleCatchUp(List.of(THURSDAY, FRIDAY), List.of(ai.id()));
    recorder.recordHistory(
        ai.id(), List.of(FakeProviders.euronextClose(ai.id(), FRIDAY, "173.16")));
    eodhd.history(AI, close(THURSDAY_TEXT, "172.50"), close(FRIDAY_TEXT, "173.00"));
    trackedWithoutHistory(TTE, "TTE");

    maintenance(15).run();

    assertThat(eodhd.calls()).first().isEqualTo("eod AI 2026-10-15 2026-10-16");
    assertThat(queue.pending()).isEmpty();
    assertThat(prices.findBetween(ai.id(), THURSDAY, FRIDAY))
        .extracting(DailyClose::source)
        .containsExactly(PriceSource.EODHD, PriceSource.EURONEXT);
    assertThat(
            jdbc.sql("SELECT closes_count FROM market_collection WHERE trading_date = :d")
                .param("d", THURSDAY)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
    assertThat(output.getAll())
        .as("instrument and date, never the prices (criterion 12)")
        .contains("Euronext and EODHD closes differ: count=1, closes=[" + ai.id() + "@2026-10-16]")
        .doesNotContain("173.00");
  }

  @Test
  void exhaustedBudgetPostponesTheRemainingCallsToTheNextDay() {
    Instrument ai = trackedWithHistory(AI, "AI");
    Instrument tte = trackedWithHistory(TTE, "TTE");
    recorder.scheduleCatchUp(List.of(THURSDAY), List.of(ai.id(), tte.id()));

    maintenance(1).run();
    maintenance(1).run();
    assertThat(eodhd.calls()).hasSize(1);
    assertThat(queue.pending()).hasSize(1);

    clock.set(MONDAY.plus(Duration.ofDays(1)));
    maintenance(1).run();
    assertThat(eodhd.calls()).hasSize(2);
    assertThat(queue.pending()).isEmpty();
  }

  @Test
  void providerFailureKeepsThePendingWork() {
    Instrument ai = trackedWithHistory(AI, "AI");
    recorder.scheduleCatchUp(List.of(THURSDAY), List.of(ai.id()));
    trackedWithoutHistory(TTE, "TTE");
    eodhd.failing(true);
    EodhdMaintenance job = maintenance(15);

    job.run();
    assertThat(eodhd.calls()).as("stops at the first failure").hasSize(1);
    job.run();
    job.run();
    assertThat(eodhd.calls())
        .as("a failed instrument waits for the next day, the others still get their turn")
        .containsExactly("eod AI 2026-10-15 2026-10-15", "eod TTE 2025-10-19 2026-10-16");
    assertThat(queue.pending()).hasSize(1);
    assertThat(find(TTE).historyLoaded()).isFalse();

    clock.set(MONDAY.plus(Duration.ofDays(1)));
    eodhd.failing(false);
    job.run();
    assertThat(queue.pending()).isEmpty();
    assertThat(find(TTE).historyLoaded()).isTrue();
  }

  @Test
  void failedOrEmptyReferenceDataIsRetriedTheNextDayOnly() {
    EodhdMaintenance job = maintenance(15);

    job.run();
    job.run();
    assertThat(eodhd.calls()).as("empty list: a failure").containsExactly(REFERENCE_CALL);

    clock.set(MONDAY.plus(Duration.ofDays(1)));
    eodhd.reference(listed(AI, "AI", AIR_LIQUIDE));
    job.run();
    assertThat(eodhd.calls()).hasSize(2);
    assertThat(instruments.lastReferenceRefresh()).isPresent();
  }

  @Test
  void withoutKeyNothingIsCalled() {
    trackedWithoutHistory(AI, "AI");
    eodhd.enabled(false);

    maintenance(15).run();

    assertThat(eodhd.calls()).isEmpty();
  }

  private EodhdMaintenance maintenance(int budget) {
    return new MaintenanceFactory(instruments, queue, usage, recorder)
        .create(eodhd, clock, budget, CW8);
  }

  private Instrument trackedWithoutHistory(Isin isin, String ticker) {
    instruments.upsertReference(List.of(listed(isin, ticker, ticker)), MONDAY);
    Instrument instrument = find(isin);
    instruments.markTracked(instrument.id(), MONDAY);
    return instrument;
  }

  private Instrument trackedWithHistory(Isin isin, String ticker) {
    Instrument instrument = trackedWithoutHistory(isin, ticker);
    instruments.markHistoryLoaded(instrument.id(), MONDAY);
    return instrument;
  }

  private Instrument find(Isin isin) {
    return instruments.search(isin.value(), 1).getFirst();
  }
}
