package dev.investai.market.infrastructure;

import static dev.investai.market.MarketTestData.EUR;
import static dev.investai.market.MarketTestData.isin;
import static dev.investai.market.MarketTestData.listed;
import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.MarketTestData;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.ListedInstrument;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.PriceSource;
import dev.investai.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class MarketPersistenceIT {

  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");
  private static final LocalDate SESSION = LocalDate.parse("2026-10-16");

  @Autowired private JdbcClient jdbc;
  @Autowired private Flyway flyway;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;

  @BeforeEach
  void clear() {
    MarketTestData.clearTables(jdbc);
  }

  @Test
  void marketMigrationIsApplied() {
    assertThat(flyway.info().applied())
        .extracting(MigrationInfo::getScript)
        .contains("V2__market_data.sql");
  }

  @Test
  void referenceUpsertIsIdempotentAndKeepsInstrumentsMissingFromTheNewList() {
    var airLiquide = listed(isin("FR000012007"), "AI", "Air Liquide");
    var total = listed(isin("FR000012027"), "TTE", "TotalEnergies");
    instruments.upsertReference(List.of(airLiquide, total), NOW);

    var renamed = listed(airLiquide.listing().isin(), "AI", "L'Air Liquide");
    instruments.upsertReference(List.of(renamed), NOW.plusSeconds(60));

    assertThat(jdbc.sql("SELECT count(*) FROM instrument").query(Integer.class).single())
        .isEqualTo(2);
    assertThat(instruments.findByListing(airLiquide.listing()))
        .hasValueSatisfying(found -> assertThat(found.name()).isEqualTo("L'Air Liquide"));
    assertThat(instruments.findByListing(total.listing())).isPresent();
    assertThat(instruments.lastReferenceRefresh()).hasValue(NOW.plusSeconds(60));
  }

  @Test
  void sameIsinOnTwoVenuesIsTwoInstruments() {
    var isin = isin("LU168104359");
    instruments.upsertReference(
        List.of(
            listed(isin, "CW8", "Amundi MSCI World"),
            new ListedInstrument(
                new Listing(isin, new Mic("ETFP")),
                "CW8",
                "Amundi MSCI World",
                InstrumentType.ETF,
                EUR)),
        NOW);

    assertThat(jdbc.sql("SELECT count(*) FROM instrument").query(Integer.class).single())
        .isEqualTo(2);
  }

  @Test
  void lastReferenceRefreshIsEmptyBeforeTheFirstLoad() {
    assertThat(instruments.lastReferenceRefresh()).isEmpty();
  }

  @Test
  void trackingIsIdempotentAndHistoryFlagIsStored() {
    Instrument instrument = load(listed(isin("FR000012007"), "AI", "Air Liquide"));

    assertThat(instruments.markTracked(instrument.id(), NOW)).isTrue();
    assertThat(instruments.markTracked(instrument.id(), NOW.plusSeconds(1))).isFalse();
    instruments.markHistoryLoaded(instrument.id(), NOW);

    assertThat(instruments.findById(instrument.id()))
        .hasValueSatisfying(
            found -> {
              assertThat(found.trackedSince()).isEqualTo(NOW);
              assertThat(found.historyLoaded()).isTrue();
            });
    assertThat(instruments.findTracked())
        .extracting(Instrument::id)
        .containsExactly(instrument.id());
  }

  @Test
  void searchMatchesIsinTickerOrNameFragmentTrackedFirstThenByName() {
    Instrument societe = load(listed(isin("FR000013030"), "GLE", "Société Générale"));
    Instrument sodexo = load(listed(isin("FR000012122"), "SW", "Sodexo"));
    Instrument total = load(listed(isin("FR000012027"), "TTE", "TotalEnergies"));
    instruments.markTracked(sodexo.id(), NOW);

    assertThat(instruments.search("SOCIETE", 20))
        .extracting(Instrument::id)
        .containsExactly(societe.id());
    assertThat(instruments.search("so", 20))
        .extracting(Instrument::id)
        .containsExactly(sodexo.id(), societe.id());
    assertThat(instruments.search("tte", 20))
        .extracting(Instrument::id)
        .containsExactly(total.id());
    assertThat(instruments.search(total.listing().isin().value(), 20))
        .extracting(Instrument::id)
        .containsExactly(total.id());
    assertThat(instruments.search("o", 2)).hasSize(2);
    assertThat(instruments.search("%", 20)).isEmpty();
  }

  @Test
  void closesAreUpsertedAndEuronextIsNeverReplacedByEodhd() {
    Instrument instrument = load(listed(isin("FR000012007"), "AI", "Air Liquide"));
    var official = close(instrument, SESSION, "173.16", PriceSource.EURONEXT);

    prices.save(List.of(official), NOW);
    prices.save(List.of(official), NOW);
    prices.save(List.of(close(instrument, SESSION, "170", PriceSource.EODHD)), NOW);

    assertThat(prices.findBetween(instrument.id(), SESSION, SESSION)).containsExactly(official);
    assertThat(prices.count(SESSION, PriceSource.EURONEXT)).isEqualTo(1);
  }

  @Test
  void closesAreReadByPeriodInDateOrder() {
    Instrument instrument = load(listed(isin("FR000012007"), "AI", "Air Liquide"));
    var friday = close(instrument, SESSION, "173.16", PriceSource.EURONEXT);
    var thursday = close(instrument, SESSION.minusDays(1), "172", PriceSource.EODHD);
    prices.save(List.of(friday, thursday), NOW);

    assertThat(prices.findBetween(instrument.id(), SESSION.minusDays(7), SESSION))
        .containsExactly(thursday, friday);
    assertThat(prices.findLatest(instrument.id())).hasValue(friday);
    assertThat(prices.find(Set.of(instrument.id()), SESSION, SESSION))
        .containsOnlyKeys(friday.key());
    assertThat(prices.find(Set.of(), SESSION, SESSION)).isEmpty();
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
