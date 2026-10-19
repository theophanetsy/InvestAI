package dev.investai.market.application;

import static dev.investai.market.MarketTestData.isin;
import static dev.investai.market.MarketTestData.listed;
import static dev.investai.market.application.FakeProviders.close;
import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.MarketTestData;
import dev.investai.market.domain.ApiCallCounter;
import dev.investai.market.domain.CatchUpQueue;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.PriceSource;
import dev.investai.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * EODHD maintenance on the real database with a fake provider: catch-up first, then history, then
 * reference data, within the daily budget (spec 2.1, criteria 4, 7, 8, 13 and 16).
 */
@IntegrationTest
class EodhdMaintenanceIT {

  private static final Instant MONDAY = Instant.parse("2026-10-19T08:00:00Z");
  private static final LocalDate THURSDAY = LocalDate.parse("2026-10-15");
  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final Isin AI = isin("FR000012007");
  private static final Isin TTE = isin("FR000012027");
  private static final Isin CW8 = isin("LU168104359");

  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;
  @Autowired private CatchUpQueue queue;
  @Autowired private ApiCallCounter usage;
  @Autowired private PriceRecorder recorder;

  private final FakeProviders.Eodhd eodhd = new FakeProviders.Eodhd();
  private final FakeProviders.MutableClock clock = new FakeProviders.MutableClock(MONDAY);

  @BeforeEach
  void clear() {
    MarketTestData.clearTables(jdbc);
  }

  @Test
  void firstStartLoadsReferenceDataThenTracksBenchmarksAndLoadsTheirHistory() {
    eodhd.reference(listed(AI, "AI", "Air Liquide"), listed(CW8, "CW8", "Amundi MSCI World"));
    eodhd.history(CW8, close("2026-10-15", "705.10"), close("2026-10-16", "709.55"));

    maintenance(15).run();

    assertThat(eodhd.calls())
        .containsExactly("exchange-symbol-list", "eod CW8 2025-10-19 2026-10-19");
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
    eodhd.reference(listed(AI, "AI", "Air Liquide"));
    maintenance(15).run();

    clock.set(MONDAY.plus(Duration.ofDays(6)));
    maintenance(15).run();
    clock.set(MONDAY.plus(Duration.ofDays(8)));
    maintenance(15).run();

    assertThat(eodhd.calls()).containsExactly("exchange-symbol-list", "exchange-symbol-list");
  }

  @Test
  void trackedInstrumentGetsOneYearOfRawClosesOnce() {
    Instrument ai = trackedWithoutHistory(AI, "AI");
    eodhd.history(AI, close("2025-10-20", "173.04"), close("2026-10-16", "173.16"));

    maintenance(15).run();
    maintenance(15).run();

    assertThat(eodhd.calls()).containsExactly("eod AI 2025-10-19 2026-10-19");
    assertThat(prices.findBetween(ai.id(), LocalDate.parse("2025-10-19"), FRIDAY))
        .extracting(close -> close.close().toPlainString())
        .containsExactly("173.0400", "173.1600");
  }

  @Test
  void catchUpComesFirstAndNeverReplacesEuronextCloses() {
    Instrument ai = trackedWithHistory(AI, "AI");
    recorder.scheduleCatchUp(List.of(THURSDAY), List.of(ai.id()));
    recorder.recordHistory(
        ai.id(), List.of(FakeProviders.euronextClose(ai.id(), FRIDAY, "173.16")));
    eodhd.history(AI, close("2026-10-15", "172.50"), close("2026-10-16", "173.00"));
    trackedWithoutHistory(TTE, "TTE");

    maintenance(15).run();

    assertThat(eodhd.calls()).first().isEqualTo("eod AI 2026-10-15 2026-10-15");
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

    maintenance(15).run();

    assertThat(eodhd.calls()).as("stops at the first failure").hasSize(1);
    assertThat(queue.pending()).hasSize(1);
    assertThat(find(TTE).historyLoaded()).isFalse();
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
        .create(eodhd, clock, budget, new Listing(CW8, Mic.XPAR));
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
    return instruments.findByListing(new Listing(isin, Mic.XPAR)).orElseThrow();
  }
}
