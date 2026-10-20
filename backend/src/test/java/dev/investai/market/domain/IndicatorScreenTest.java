package dev.investai.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.investai.market.MarketTestData;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    var criteria = criteria(null, null, BigDecimal.ONE, null, ScreeningSort.LIQUIDITY, 50);

    assertThat(IndicatorScreen.apply(all, criteria).results())
        .as("most liquid first, no dollar amount compared with euros")
        .containsExactly(foreign, calm, risky);
  }

  @Test
  void minimumPerformanceUsesTheChosenWindow() {
    var criteria =
        new ScreeningCriteria(
            EnumSet.allOf(InstrumentType.class),
            EnumSet.allOf(PeaEligibilityStatus.class),
            null,
            null,
            null,
            IndicatorWindow.ONE_YEAR,
            BigDecimal.TEN,
            ScreeningSort.PERFORMANCE,
            50);
    var otherWindow =
        new ScreeningCriteria(
            EnumSet.allOf(InstrumentType.class),
            EnumSet.allOf(PeaEligibilityStatus.class),
            null,
            null,
            null,
            IndicatorWindow.ONE_MONTH,
            BigDecimal.TEN,
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

  @Test
  void keepsTheRequestedTypesAndEligibilities() {
    // An ETF whose eligibility the rule cannot tell (spec 2.3) next to the stocks.
    InstrumentIndicators etf =
        new InstrumentIndicators(
            instrument("Monde", InstrumentType.ETF, PeaEligibilityStatus.UNKNOWN),
            stored("9", "14", "-11", "3000000"));
    var everything = List.of(calm, risky, foreign, etf);
    var eligibleOrUnknown = Set.of(PeaEligibilityStatus.ELIGIBLE, PeaEligibilityStatus.UNKNOWN);

    assertThat(screen(everything, Set.of(InstrumentType.ETF), eligibleOrUnknown).results())
        .containsExactly(etf);
    assertThat(screen(everything, EnumSet.allOf(InstrumentType.class), eligibleOrUnknown).results())
        .as("by volatility: 12, 14, 45")
        .containsExactly(calm, etf, risky);
    assertThat(
            screen(everything, Set.of(InstrumentType.STOCK), Set.of(PeaEligibilityStatus.ELIGIBLE))
                .results())
        .containsExactly(calm, risky);
  }

  @Test
  void maximumDrawdownKeepsTheInstrumentsThatFellAtMostThatMuch() {
    // Limit 15 %: falls of 9 (calm), 10 (dollar) and exactly 15 (foreign) kept, 38 (risky) left
    // out, no drawdown (young) never matches.
    var criteria = filters(null, new BigDecimal("15"), null, null);

    assertThat(IndicatorScreen.apply(all, criteria).results())
        .containsExactly(calm, dollar, foreign);
  }

  @Test
  void countsTheInstrumentsLeftOutForWantOfEachRequestedIndicator() {
    // Young has no indicator at all; dollar has no amount in euros. Bounds wide enough to keep
    // every other instrument: calm, risky and foreign match.
    var hundred = new BigDecimal("100");
    var criteria = filters(hundred, hundred, BigDecimal.ZERO, new BigDecimal("-100"));

    Screening screening = IndicatorScreen.apply(all, criteria);

    assertThat(screening.matchCount()).isEqualTo(3);
    assertThat(screening.unavailable())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                ScreeningFilter.VOLATILITY, 1,
                ScreeningFilter.MAX_DRAWDOWN, 1,
                ScreeningFilter.LIQUIDITY, 2,
                ScreeningFilter.PERFORMANCE, 1));
  }

  @Test
  void countsOnlyTheRequestedFiltersAmongTheRequestedTypesAndEligibilities() {
    var eligibleStocks =
        new ScreeningCriteria(
            Set.of(InstrumentType.STOCK),
            Set.of(PeaEligibilityStatus.ELIGIBLE),
            new BigDecimal("100"),
            null,
            null,
            IndicatorWindow.ONE_YEAR,
            null,
            ScreeningSort.VOLATILITY,
            50);

    // Young (no volatility) is eligible: counted. Nothing else requested, nothing else counted.
    assertThat(IndicatorScreen.apply(all, eligibleStocks).unavailable())
        .containsExactlyEntriesOf(Map.of(ScreeningFilter.VOLATILITY, 1));
    assertThat(IndicatorScreen.apply(List.of(foreign, young), eligibleStocks).unavailable())
        .as("young is the only eligible one")
        .containsExactlyEntriesOf(Map.of(ScreeningFilter.VOLATILITY, 1));
    assertThat(
            IndicatorScreen.apply(
                    all, criteria(null, null, null, null, ScreeningSort.VOLATILITY, 50))
                .unavailable())
        .isEmpty();
  }

  @Test
  void criteriaNeedATypeAnEligibilityAndAPositiveDrawdownLimit() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> screen(all, Set.of(), Set.of(PeaEligibilityStatus.ELIGIBLE)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> screen(all, Set.of(InstrumentType.ETF), Set.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> filters(null, new BigDecimal("-1"), null, null));
  }

  private static Screening screen(
      List<InstrumentIndicators> candidates,
      Set<InstrumentType> types,
      Set<PeaEligibilityStatus> eligibilities) {
    return IndicatorScreen.apply(
        candidates,
        new ScreeningCriteria(
            types,
            eligibilities,
            null,
            null,
            null,
            IndicatorWindow.ONE_YEAR,
            null,
            ScreeningSort.VOLATILITY,
            50));
  }

  private static ScreeningCriteria filters(
      @Nullable BigDecimal maxVolatility,
      @Nullable BigDecimal maxDrawdownLimit,
      @Nullable BigDecimal minAmount,
      @Nullable BigDecimal minPerformance) {
    return new ScreeningCriteria(
        EnumSet.allOf(InstrumentType.class),
        EnumSet.allOf(PeaEligibilityStatus.class),
        maxVolatility,
        maxDrawdownLimit,
        minAmount,
        IndicatorWindow.ONE_YEAR,
        minPerformance,
        ScreeningSort.MAX_DRAWDOWN,
        50);
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
        EnumSet.allOf(InstrumentType.class),
        eligibility == null ? EnumSet.allOf(PeaEligibilityStatus.class) : Set.of(eligibility),
        maxVolatility,
        null,
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
      String name, InstrumentType type, PeaEligibilityStatus eligibility) {
    Instrument stock = instrument(name, eligibility, MarketTestData.EUR);
    return new Instrument(
        stock.id(),
        stock.listing(),
        stock.ticker(),
        name,
        type,
        MarketTestData.EUR,
        null,
        false,
        stock.peaEligibility());
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
