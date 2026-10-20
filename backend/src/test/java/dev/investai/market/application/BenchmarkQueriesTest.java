package dev.investai.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.investai.market.IndicatorFixtures;
import dev.investai.market.MarketTestData;
import dev.investai.market.domain.BenchmarkRole;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.IndicatorRun;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.PeaEligibility;
import dev.investai.market.domain.PeaEligibilityStatus;
import dev.investai.market.domain.StoredIndicators;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The reference ETFs of the MCP tool getBenchmarks (spec 3.1, criteria 26 and 27). */
class BenchmarkQueriesTest {

  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final Instant COMPUTED_AT = Instant.parse("2026-10-17T07:00:00Z");
  private static final Listing CAC = listing("FR000705278");
  private static final Listing CW8 = listing("LU168104359");
  private static final Listing WPEA = listing("IE0002XZSHO");
  private static final Listing MISSING = listing("FR000000000");

  private final InstrumentRepository instruments = mock(InstrumentRepository.class);
  private final DailyPriceRepository prices = mock(DailyPriceRepository.class);
  private final IndicatorRepository indicators = mock(IndicatorRepository.class);
  private final BenchmarkQueries queries =
      new BenchmarkQueries(
          instruments,
          prices,
          indicators,
          new MarketSettings(15, Duration.ofDays(7), List.of(CAC, MISSING, CW8, WPEA)),
          new IndicatorSettings(CW8, CAC));

  @Test
  void listsTheConfiguredBenchmarksInOrderWithTheirRoleCloseAndIndicators() {
    Instrument cac = etf(CAC);
    Instrument cw8 = etf(CW8);
    Instrument wpea = etf(WPEA);
    for (Instrument etf : List.of(cac, cw8, wpea)) {
      when(instruments.findByListing(etf.listing())).thenReturn(Optional.of(etf));
    }
    when(instruments.findByListing(MISSING)).thenReturn(Optional.empty());
    DailyClose close = IndicatorFixtures.close(cw8.id(), FRIDAY, "520.10");
    when(prices.findLatest(anyCollection())).thenReturn(Map.of(cw8.id(), close));
    var stored = new StoredIndicators(cac.id(), IndicatorFixtures.sampleSet(FRIDAY), COMPUTED_AT);
    when(indicators.find(cac.id())).thenReturn(Optional.of(stored));
    when(indicators.find(cw8.id())).thenReturn(Optional.empty());
    when(indicators.find(wpea.id())).thenReturn(Optional.empty());
    var run = new IndicatorRun(FRIDAY, COMPUTED_AT);
    when(indicators.lastRun()).thenReturn(Optional.of(run));

    Benchmarks benchmarks = queries.benchmarks();

    assertThat(benchmarks.lastRun()).isEqualTo(run);
    // The listing absent from the reference data is skipped.
    assertThat(benchmarks.benchmarks())
        .extracting(Benchmark::role)
        .containsExactly(
            BenchmarkRole.CAC40_REFERENCE, BenchmarkRole.MSCI_WORLD_REFERENCE, BenchmarkRole.OTHER);
    assertThat(benchmarks.benchmarks())
        .extracting(benchmark -> benchmark.snapshot().instrument())
        .containsExactly(cac, cw8, wpea);
    assertThat(benchmarks.benchmarks().get(0).snapshot().indicators()).isEqualTo(stored);
    assertThat(benchmarks.benchmarks().get(1).snapshot().lastClose()).isEqualTo(close);
    assertThat(benchmarks.benchmarks().get(1).snapshot().indicators()).isNull();
  }

  @Test
  void noBenchmarkInTheReferenceDataYet() {
    when(instruments.findByListing(any())).thenReturn(Optional.empty());
    when(prices.findLatest(anyCollection())).thenReturn(Map.of());
    when(indicators.lastRun()).thenReturn(Optional.empty());

    Benchmarks benchmarks = queries.benchmarks();

    assertThat(benchmarks.benchmarks()).isEmpty();
    assertThat(benchmarks.lastRun()).isNull();
  }

  private static Listing listing(String isinBody) {
    return new Listing(MarketTestData.isin(isinBody), Mic.XPAR);
  }

  private static Instrument etf(Listing listing) {
    return new Instrument(
        InstrumentId.newId(),
        listing,
        "ETF",
        "ETF " + listing.isin(),
        InstrumentType.ETF,
        MarketTestData.EUR,
        COMPUTED_AT,
        true,
        PeaEligibility.byRule(PeaEligibilityStatus.ELIGIBLE));
  }
}
