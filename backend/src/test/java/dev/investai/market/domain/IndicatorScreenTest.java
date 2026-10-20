package dev.investai.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.investai.market.MarketTestData;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Screening of every Paris instrument on its stored indicators (spec 2.4, criteria 6 and 9). */
class IndicatorScreenTest {

  private static final LocalDate AS_OF = LocalDate.parse("2026-10-16");
  private static final Instant COMPUTED_AT = Instant.parse("2026-10-17T07:00:00Z");
  private static final Currency USD = Currency.getInstance("USD");

  // name, eligibility, one-year performance, volatility, drawdown, amount (null = insufficient)
  private final InstrumentIndicators calm =
      candidate("Calme", PeaEligibilityStatus.ELIGIBLE, "8", "12", "-9", "2000000");
  private final InstrumentIndicators risky =
      candidate("Risquée", PeaEligibilityStatus.ELIGIBLE, "40", "45", "-38", "900000");
  private final InstrumentIndicators foreign =
      candidate("Étrangère", PeaEligibilityStatus.NOT_ELIGIBLE, "15", "20", "-15", "5000000");
  private final InstrumentIndicators young =
      candidate("Récente", PeaEligibilityStatus.ELIGIBLE, null, null, null, null);
  private final InstrumentIndicators dollar =
      new InstrumentIndicators(
          instrument("Dollar", PeaEligibilityStatus.ELIGIBLE, USD),
          stored("10", "15", "-10", "9000000"));
  private final List<InstrumentIndicators> all = List.of(calm, risky, foreign, young, dollar);

  @Test
  void eligibleLowVolatilityAndLiquidInstrumentsLikeTheAgentWillAsk() {
    // "Valeurs éligibles PEA, volatilité inférieure à 25 %, liquides" (criterion 6).
    var criteria =
        criteria(
            PeaEligibilityStatus.ELIGIBLE,
            new BigDecimal("25"),
            new BigDecimal("1000000"),
            null,
            ScreeningSort.PERFORMANCE,
            50);

    Screening screening = IndicatorScreen.apply(all, criteria);

    assertThat(screening.results()).containsExactly(calm);
    assertThat(screening.matchCount()).isEqualTo(1);
  }

  @Test
  void anInstrumentWithoutTheFilteredIndicatorNeverMatches() {
    var criteria = criteria(null, new BigDecimal("100"), null, null, ScreeningSort.VOLATILITY, 50);

    assertThat(IndicatorScreen.apply(all, criteria).results()).doesNotContain(young);
  }

  @Test
  void liquidityIsOnlyComparedInEuros() {
    var criteria = criteria(null, null, new BigDecimal("1"), null, ScreeningSort.LIQUIDITY, 50);

    assertThat(IndicatorScreen.apply(all, criteria).results())
        .as("most liquid first, no dollar amount compared with euros")
        .containsExactly(foreign, calm, risky);
  }

  @Test
  void minimumPerformanceUsesTheChosenWindow() {
    var criteria =
        new ScreeningCriteria(
            null,
            null,
            null,
            IndicatorWindow.ONE_YEAR,
            new BigDecimal("10"),
            ScreeningSort.PERFORMANCE,
            50);
    var otherWindow =
        new ScreeningCriteria(
            null,
            null,
            null,
            IndicatorWindow.ONE_MONTH,
            new BigDecimal("10"),
            ScreeningSort.PERFORMANCE,
            50);

    assertThat(IndicatorScreen.apply(all, criteria).results())
        .containsExactly(risky, foreign, dollar);
    assertThat(IndicatorScreen.apply(all, otherWindow).results())
        .as("one-month performance unknown in this fixture")
        .isEmpty();
  }

  @Test
  void sortsPutInstrumentsWithoutTheIndicatorLast() {
    assertThat(sorted(ScreeningSort.PERFORMANCE))
        .containsExactly(risky, foreign, dollar, calm, young);
    assertThat(sorted(ScreeningSort.VOLATILITY))
        .containsExactly(calm, dollar, foreign, risky, young);
    assertThat(sorted(ScreeningSort.MAX_DRAWDOWN))
        .as("smallest fall first")
        .containsExactly(calm, dollar, foreign, risky, young);
    assertThat(sorted(ScreeningSort.LIQUIDITY))
        .as("an amount in dollars is not ranked with amounts in euros")
        .containsExactly(foreign, calm, risky, dollar, young);
  }

  @Test
  void equalValuesAreOrderedByName() {
    InstrumentIndicators twin =
        candidate("Alpha", PeaEligibilityStatus.ELIGIBLE, "8", "12", "-9", "2000000");

    var criteria = criteria(null, null, null, null, ScreeningSort.VOLATILITY, 2);

    assertThat(IndicatorScreen.apply(List.of(calm, twin), criteria).results())
        .containsExactly(twin, calm);
  }

  @Test
  void resultsAreLimitedButTheMatchesCounted() {
    var criteria = criteria(null, null, null, null, ScreeningSort.PERFORMANCE, 2);

    Screening screening = IndicatorScreen.apply(all, criteria);

    assertThat(screening.results()).containsExactly(risky, foreign);
    assertThat(screening.matchCount()).isEqualTo(5);
  }

  @Test
  void limitIsBetweenOneAndFifty() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> criteria(null, null, null, null, ScreeningSort.PERFORMANCE, 0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> criteria(null, null, null, null, ScreeningSort.PERFORMANCE, 51));
  }

  private List<InstrumentIndicators> sorted(ScreeningSort sort) {
    return IndicatorScreen.apply(all, criteria(null, null, null, null, sort, 50)).results();
  }

  private static ScreeningCriteria criteria(
      @Nullable PeaEligibilityStatus eligibility,
      @Nullable BigDecimal maxVolatility,
      @Nullable BigDecimal minAmount,
      @Nullable BigDecimal minPerformance,
      ScreeningSort sort,
      int limit) {
    return new ScreeningCriteria(
        eligibility,
        maxVolatility,
        minAmount,
        IndicatorWindow.ONE_YEAR,
        minPerformance,
        sort,
        limit);
  }

  private static InstrumentIndicators candidate(
      String name,
      PeaEligibilityStatus eligibility,
      @Nullable String performance,
      @Nullable String volatility,
      @Nullable String drawdown,
      @Nullable String amount) {
    return new InstrumentIndicators(
        instrument(name, eligibility, MarketTestData.EUR),
        stored(performance, volatility, drawdown, amount));
  }

  private static Instrument instrument(
      String name, PeaEligibilityStatus eligibility, Currency currency) {
    return new Instrument(
        InstrumentId.newId(),
        new Listing(
            MarketTestData.isin(
                String.format("FR%09d", Math.floorMod(name.hashCode(), 1_000_000_000))),
            Mic.XPAR),
        name.substring(0, 2).toUpperCase(java.util.Locale.ROOT),
        name,
        InstrumentType.STOCK,
        currency,
        null,
        false,
        PeaEligibility.byRule(eligibility));
  }

  private static StoredIndicators stored(
      @Nullable String performance,
      @Nullable String volatility,
      @Nullable String drawdown,
      @Nullable String amount) {
    Map<IndicatorWindow, Indicator> performances = new EnumMap<>(IndicatorWindow.class);
    for (IndicatorWindow window : IndicatorWindow.values()) {
      performances.put(
          window,
          window == IndicatorWindow.ONE_YEAR
              ? indicator(performance)
              : Indicator.INSUFFICIENT_HISTORY);
    }
    var set =
        new IndicatorSet(
            AS_OF,
            253,
            performances,
            indicator(volatility),
            indicator(drawdown),
            BenchmarkComparison.INSUFFICIENT_HISTORY,
            BenchmarkComparison.INSUFFICIENT_HISTORY,
            indicator(amount));
    return new StoredIndicators(InstrumentId.newId(), set, COMPUTED_AT);
  }

  private static Indicator indicator(@Nullable String value) {
    return value == null ? Indicator.INSUFFICIENT_HISTORY : Indicator.of(new BigDecimal(value));
  }
}
