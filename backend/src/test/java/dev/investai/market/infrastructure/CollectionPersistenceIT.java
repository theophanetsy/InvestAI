package dev.investai.market.infrastructure;

import static dev.investai.market.MarketTestData.EUR;
import static dev.investai.market.MarketTestData.isin;
import static dev.investai.market.MarketTestData.listed;
import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.MarketTestData;
import dev.investai.market.domain.ApiCallCounter;
import dev.investai.market.domain.CatchUpQueue;
import dev.investai.market.domain.CatchUpQueue.CatchUpRequest;
import dev.investai.market.domain.CollectionJournal;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.ListedInstrument;
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

@IntegrationTest
class CollectionPersistenceIT {

  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");
  private static final LocalDate SESSION = LocalDate.parse("2026-10-16");

  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;
  @Autowired private CollectionJournal journal;
  @Autowired private CatchUpQueue catchUp;
  @Autowired private ApiCallCounter usage;

  @BeforeEach
  void clear() {
    MarketTestData.clearTables(jdbc);
  }

  @Test
  void journalKnowsCollectedSegmentsAndCaughtUpSessions() {
    journal.recordFile(SESSION, TradeFileSegment.EQUITIES, 3, NOW);
    journal.recordFile(SESSION, TradeFileSegment.EQUITIES, 3, NOW);
    journal.recordCatchUp(List.of(SESSION.minusDays(1)), NOW);
    journal.recordCatchUp(List.of(SESSION.minusDays(1)), NOW);

    assertThat(journal.isCollected(SESSION, TradeFileSegment.EQUITIES)).isTrue();
    assertThat(journal.isCollected(SESSION, TradeFileSegment.ETFS_FUNDS)).isFalse();
    assertThat(journal.isCollected(SESSION.minusDays(1), TradeFileSegment.ETFS_FUNDS)).isTrue();
    assertThat(journal.lastSessionBefore(SESSION)).hasValue(SESSION.minusDays(1));
    assertThat(journal.lastSessionBefore(SESSION.minusDays(1))).isEmpty();
    assertThat(jdbc.sql("SELECT count(*) FROM market_collection").query(Integer.class).single())
        .isEqualTo(2);
  }

  @Test
  void caughtUpSessionsCountTheirEodhdCloses() {
    Instrument instrument = load(listed(isin("FR000012007"), "AI", "Air Liquide"));
    LocalDate missed = SESSION.minusDays(1);
    journal.recordCatchUp(List.of(missed), NOW);
    prices.save(List.of(close(instrument, missed, "172", PriceSource.EODHD)), NOW);

    journal.refreshCatchUpCounts(missed, missed);

    assertThat(
            jdbc.sql("SELECT closes_count FROM market_collection WHERE trading_date = :d")
                .param("d", missed)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void catchUpEntriesAreWidenedThenRemoved() {
    Instrument instrument = load(listed(isin("FR000012007"), "AI", "Air Liquide"));
    catchUp.enqueue(List.of(instrument.id()), SESSION.minusDays(3), SESSION.minusDays(2), NOW);
    catchUp.enqueue(List.of(instrument.id()), SESSION.minusDays(1), SESSION.minusDays(1), NOW);

    assertThat(catchUp.pending())
        .containsExactly(
            new CatchUpRequest(instrument.id(), SESSION.minusDays(3), SESSION.minusDays(1)));

    catchUp.remove(instrument.id());
    assertThat(catchUp.pending()).isEmpty();
  }

  @Test
  void callBudgetIsReservedUpToTheLimitPerDay() {
    assertThat(usage.reserve(SESSION, 2)).hasValue(1);
    assertThat(usage.reserve(SESSION, 2)).hasValue(2);
    assertThat(usage.reserve(SESSION, 2)).isEmpty();
    assertThat(usage.reserve(SESSION.plusDays(1), 2)).hasValue(1);
    assertThat(usage.reserve(SESSION.plusDays(2), 0)).isEmpty();
  }

  private Instrument load(ListedInstrument entry) {
    instruments.upsertReference(List.of(entry), NOW);
    return instruments.findByListing(entry.listing()).orElseThrow();
  }

  private static DailyClose close(
      Instrument instrument, LocalDate day, String price, PriceSource source) {
    return new DailyClose(instrument.id(), day, new BigDecimal(price), EUR, source);
  }
}
