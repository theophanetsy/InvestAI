package dev.investai.market.infrastructure;

import static dev.investai.market.MarketTestData.EUR;
import static dev.investai.market.MarketTestData.isin;
import static dev.investai.market.MarketTestData.listed;
import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.MarketTestData;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.ListedInstrument;
import dev.investai.market.domain.PeaEligibility;
import dev.investai.market.domain.PeaEligibilityStatus;
import dev.investai.market.domain.PriceSource;
import dev.investai.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** PEA eligibility and batch reads on the real database (spec 2.3, criteria 7 and 8). */
@IntegrationTest
class PeaEligibilityPersistenceIT {

  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;

  @BeforeEach
  void clear() {
    MarketTestData.clearTables(jdbc);
  }

  @Test
  void ruleAppliesWhenReading() {
    Instrument french = load(listed(isin("FR000012007"), "AI", "Air Liquide"));
    Instrument american = load(listed(isin("US037833100"), "AAPL", "Apple"));
    Instrument fund = load(etf(isin("FR001040074"), "ESE", "BNP S&P 500"));
    // Benchmark configured in application.properties (Amundi MSCI World, CW8).
    Instrument benchmark = load(etf(isin("LU168104359"), "CW8", "Amundi MSCI World"));

    assertThat(french.peaEligibility())
        .isEqualTo(PeaEligibility.byRule(PeaEligibilityStatus.ELIGIBLE));
    assertThat(american.peaEligibility())
        .isEqualTo(PeaEligibility.byRule(PeaEligibilityStatus.NOT_ELIGIBLE));
    assertThat(fund.peaEligibility())
        .isEqualTo(PeaEligibility.byRule(PeaEligibilityStatus.UNKNOWN));
    assertThat(benchmark.peaEligibility())
        .isEqualTo(PeaEligibility.byRule(PeaEligibilityStatus.ELIGIBLE));
  }

  @Test
  void ownerCorrectionIsNeverOverwrittenByTheWeeklyReferenceRefresh() {
    ListedInstrument entry = etf(isin("FR001040074"), "ESE", "BNP S&P 500");
    Instrument fund = load(entry);

    assertThat(instruments.correctPeaEligibility(fund.id(), PeaEligibilityStatus.ELIGIBLE, NOW))
        .isTrue();
    // The refresh renames the fund and even turns it into a share from the United States.
    instruments.upsertReference(
        List.of(
            new ListedInstrument(
                entry.listing(), "ESE", "BNP Paribas S&P 500", InstrumentType.STOCK, EUR)),
        NOW.plusSeconds(3600));

    assertThat(instruments.findById(fund.id()))
        .hasValueSatisfying(
            found -> {
              assertThat(found.name()).isEqualTo("BNP Paribas S&P 500");
              assertThat(found.peaEligibility())
                  .isEqualTo(PeaEligibility.byOwner(PeaEligibilityStatus.ELIGIBLE));
            });
  }

  @Test
  void ownerCanCorrectAgainAndEveryReadSeesTheCorrection() {
    Instrument french = load(listed(isin("FR000012007"), "AI", "Air Liquide"));

    instruments.correctPeaEligibility(french.id(), PeaEligibilityStatus.UNKNOWN, NOW);
    instruments.correctPeaEligibility(
        french.id(), PeaEligibilityStatus.NOT_ELIGIBLE, NOW.plusSeconds(1));

    var expected = PeaEligibility.byOwner(PeaEligibilityStatus.NOT_ELIGIBLE);
    assertThat(instruments.findByListing(french.listing()).orElseThrow().peaEligibility())
        .isEqualTo(expected);
    assertThat(instruments.search("air", 20))
        .extracting(Instrument::peaEligibility)
        .containsExactly(expected);
    assertThat(instruments.findAll())
        .extracting(Instrument::peaEligibility)
        .containsExactly(expected);
  }

  @Test
  void correctingAnUnknownInstrumentChangesNothing() {
    assertThat(
            instruments.correctPeaEligibility(
                new InstrumentId(UUID.randomUUID()), PeaEligibilityStatus.ELIGIBLE, NOW))
        .isFalse();
  }

  @Test
  void instrumentsAndLatestClosesAreReadInOneGo() {
    Instrument airLiquide = load(listed(isin("FR000012007"), "AI", "Air Liquide"));
    Instrument total = load(listed(isin("FR000012027"), "TTE", "TotalEnergies"));
    Instrument withoutClose = load(listed(isin("FR000012122"), "SW", "Sodexo"));
    LocalDate friday = LocalDate.parse("2026-10-16");
    prices.save(
        List.of(
            close(airLiquide, friday.minusDays(1), "170"),
            close(airLiquide, friday, "173.16"),
            close(total, friday.minusDays(1), "55.10")),
        NOW);
    var ids = List.of(airLiquide.id(), total.id(), withoutClose.id());

    assertThat(instruments.findAllById(ids))
        .extracting(Instrument::id)
        .containsExactlyInAnyOrderElementsOf(ids);
    assertThat(prices.findLatest(ids))
        .containsOnlyKeys(airLiquide.id(), total.id())
        .hasEntrySatisfying(
            airLiquide.id(), latest -> assertThat(latest.tradingDate()).isEqualTo(friday));
    assertThat(instruments.findAllById(List.of())).isEmpty();
    assertThat(prices.findLatest(List.of())).isEmpty();
  }

  private Instrument load(ListedInstrument entry) {
    instruments.upsertReference(List.of(entry), NOW);
    return instruments.findByListing(entry.listing()).orElseThrow();
  }

  private static ListedInstrument etf(Isin isin, String ticker, String name) {
    return new ListedInstrument(
        listed(isin, ticker, name).listing(), ticker, name, InstrumentType.ETF, EUR);
  }

  private static DailyClose close(Instrument instrument, LocalDate day, String price) {
    return new DailyClose(instrument.id(), day, new BigDecimal(price), EUR, PriceSource.EURONEXT);
  }
}
