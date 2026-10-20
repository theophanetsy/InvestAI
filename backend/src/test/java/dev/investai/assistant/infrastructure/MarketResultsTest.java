package dev.investai.assistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.assistant.domain.DataLimit;
import dev.investai.assistant.infrastructure.ScreeningResult.Criteria;
import dev.investai.assistant.infrastructure.ScreeningResult.EligibilityFilter;
import dev.investai.assistant.infrastructure.ScreeningResult.InstrumentKind;
import dev.investai.market.IndicatorFixtures;
import dev.investai.market.application.Benchmark;
import dev.investai.market.application.Benchmarks;
import dev.investai.market.application.InstrumentSnapshot;
import dev.investai.market.application.PriceHistory;
import dev.investai.market.domain.BenchmarkRole;
import dev.investai.market.domain.IndicatorRun;
import dev.investai.market.domain.IndicatorStatus;
import dev.investai.market.domain.IndicatorWindow;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentIndicators;
import dev.investai.market.domain.Screening;
import dev.investai.market.domain.ScreeningFilter;
import dev.investai.market.domain.ScreeningSort;
import dev.investai.market.domain.StoredIndicators;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Projections of the market tools, without Spring (spec 3.1, criterion 40): formats, statuses and
 * values, dates, limits of the data and empty states.
 */
class MarketResultsTest {

  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final Instant COMPUTED_AT = Instant.parse("2026-10-17T07:00:00Z");

  private final Instrument airLiquide = IndicatorFixtures.airLiquide();
  private final StoredIndicators stored =
      new StoredIndicators(airLiquide.id(), IndicatorFixtures.sampleSet(FRIDAY), COMPUTED_AT);

  @Test
  void instrumentWithIndicatorsGivesEveryFigureWithItsStatusAndTheDates() {
    var close = IndicatorFixtures.close(airLiquide.id(), FRIDAY, "170.5");

    var result = InstrumentResult.of(new InstrumentSnapshot(airLiquide, close, stored));

    assertThat(result.instrumentId()).isEqualTo(airLiquide.id().value());
    assertThat(result.isin()).isEqualTo("FR0000120073");
    assertThat(result.mic()).isEqualTo("XPAR");
    assertThat(result.tracked()).isTrue();
    assertThat(result.lastClose()).isEqualTo(new LastClose("170.5000", FRIDAY));
    assertThat(result.indicatorsStatus()).isEqualTo(IndicatorsStatus.COMPUTED);
    assertThat(result.asOf()).isEqualTo(FRIDAY);
    assertThat(result.computedAt()).isEqualTo(COMPUTED_AT);
    assertThat(result.closesOneYear()).isEqualTo(180);
    var indicators = java.util.Objects.requireNonNull(result.indicators());
    assertThat(indicators.performancePercent().oneMonth())
        .isEqualTo(new IndicatorValue(IndicatorStatus.AVAILABLE, "2.35"));
    assertThat(indicators.performancePercent().oneYear())
        .isEqualTo(new IndicatorValue(IndicatorStatus.INSUFFICIENT_HISTORY, null));
    assertThat(indicators.maxDrawdownPercent().value()).isEqualTo("-12.30");
    assertThat(indicators.cac40().beta().value()).isEqualTo("0.00");
    assertThat(indicators.averageDailyTradedAmount())
        .isEqualTo(new IndicatorsView.TradedAmount(IndicatorStatus.AVAILABLE, "1234567.89", "EUR"));
    assertThat(result.untrusted()).isEqualTo(new UntrustedInstrument("Air Liquide", "AI"));
    assertThat(result.dataLimits()).extracting(DataLimitView::code).hasSize(6);
  }

  @Test
  void instrumentBeforeTheComputationAndWithoutCloseHasNoDate() {
    var result = InstrumentResult.of(new InstrumentSnapshot(airLiquide, null, null));

    assertThat(result.indicatorsStatus()).isEqualTo(IndicatorsStatus.NOT_COMPUTED);
    assertThat(result.asOf()).isNull();
    assertThat(result.computedAt()).isNull();
    assertThat(result.closesOneYear()).isNull();
    assertThat(result.indicators()).isNull();
    assertThat(result.lastClose()).isNull();
  }

  @Test
  void benchmarksCarryTheirRoleAndTheLimitsOnceForAll() {
    var run = new IndicatorRun(FRIDAY, COMPUTED_AT);
    var snapshot = new InstrumentSnapshot(airLiquide, null, stored);

    var result =
        BenchmarksResult.of(
            new Benchmarks(
                run, List.of(new Benchmark(BenchmarkRole.MSCI_WORLD_REFERENCE, snapshot))));
    var empty = BenchmarksResult.of(new Benchmarks(null, List.of()));

    assertThat(result.asOf()).isEqualTo(FRIDAY);
    assertThat(result.benchmarks().getFirst().role()).isEqualTo(BenchmarkRole.MSCI_WORLD_REFERENCE);
    assertThat(result.benchmarks().getFirst().indicators())
        .isEqualTo(InstrumentResult.of(snapshot).indicators());
    assertThat(result.dataLimits()).isEqualTo(DataLimitView.INSTRUMENT);
    assertThat(empty.asOf()).isNull();
    assertThat(empty.computedAt()).isNull();
  }

  @Test
  void screeningSummarisesTheChosenWindowAndCountsOnlyTheRequestedFilters() {
    var criteria =
        new Criteria(
            InstrumentKind.STOCK,
            EligibilityFilter.ELIGIBLE,
            "25",
            null,
            null,
            IndicatorWindow.ONE_MONTH,
            "0",
            ScreeningSort.PERFORMANCE,
            10);
    var screening =
        new Screening(
            7,
            List.of(new InstrumentIndicators(airLiquide, stored)),
            Map.of(ScreeningFilter.VOLATILITY, 2, ScreeningFilter.PERFORMANCE, 3));

    var result = ScreeningResult.of(criteria, new IndicatorRun(FRIDAY, COMPUTED_AT), screening);

    assertThat(result.matchCount()).isEqualTo(7);
    assertThat(result.unavailable()).isEqualTo(new ScreeningResult.Unavailable(2, null, null, 3));
    var item = result.results().getFirst();
    assertThat(item.performanceWindowPercent().value()).isEqualTo("2.35");
    assertThat(item.performanceOneYearPercent().status())
        .isEqualTo(IndicatorStatus.INSUFFICIENT_HISTORY);
    assertThat(item.closesOneYear()).isEqualTo(180);
    assertThat(
            ScreeningResult.of(
                    criteria, new IndicatorRun(FRIDAY, COMPUTED_AT), new Screening(0, List.of()))
                .unavailable())
        .isNull();
    assertThat(ScreeningResult.notComputed(criteria).indicatorsStatus())
        .isEqualTo(IndicatorsStatus.NOT_COMPUTED);
    assertThat(InstrumentKind.STOCK_OR_ETF.types()).hasSize(2);
    assertThat(EligibilityFilter.ANY.statuses()).hasSize(3);
  }

  @Test
  void historyWithoutCloseHasNoPeriod() {
    var result = PriceHistoryResult.of(new PriceHistory(airLiquide, List.of(), List.of()));

    assertThat(result.from()).isNull();
    assertThat(result.to()).isNull();
    assertThat(result.sessionCount()).isZero();
    assertThat(result.points()).isEmpty();
    assertThat(result.currency()).isEqualTo("EUR");
    assertThat(result.dataLimits())
        .extracting(DataLimitView::code)
        .containsExactly(DataLimit.RAW_CLOSES, DataLimit.OUTLIER_QUOTES);
  }
}
