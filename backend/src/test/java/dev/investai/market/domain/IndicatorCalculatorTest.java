package dev.investai.market.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.MarketTestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Indicators of an instrument (spec 2.4, criteria 1 to 5) on synthetic series of the 253 sessions
 * ending on Friday 16 October 2026 (index 0 = reference of the one-year performance, index 252 =
 * last session). Every expected value is derived by hand in the comment of the test.
 */
class IndicatorCalculatorTest {

  private static final LocalDate AS_OF = LocalDate.parse("2026-10-16");
  private static final YearWindow WINDOW = YearWindow.ending(AS_OF, new EuronextCalendar());
  private static final InstrumentId ID = InstrumentId.newId();
  private static final int LAST = YearWindow.SESSIONS - 1;
  private static final String ZERO = "0.00";
  private static final String P100 = "100";
  private static final String P105 = "105";
  private static final String P110 = "110";
  private static final String P120 = "120";
  private static final Set<LocalDate> NO_EURONEXT_FILE = Set.of();
  private static final ClosingPrices NO_CLOSE = ClosingPrices.of(WINDOW.sessions(), List.of());

  /**
   * Steps: 50 until the six-month reference, 80 until the three-month one, 100 until the one-month
   * one, 120 until the day before the last session, 150 on the last session.
   */
  private static final NavigableMap<Integer, String> STEPS =
      new TreeMap<>(
          Map.of(
              0,
              "50",
              start(IndicatorWindow.SIX_MONTHS),
              "80",
              start(IndicatorWindow.THREE_MONTHS),
              P100,
              start(IndicatorWindow.ONE_MONTH),
              P120,
              LAST,
              "150"));

  /** 100, then 120 (high), then 90 (low, -25 %), then 130 (new high), then 117 (-10 %). */
  private static final NavigableMap<Integer, String> FALLS =
      new TreeMap<>(Map.of(0, P100, 50, P120, 100, "90", 150, "130", 200, "117"));

  /** Index of the reference session of a window: the session before its N sessions. */
  private static int start(IndicatorWindow window) {
    return LAST - window.sessions();
  }

  private static String steps(int index) {
    return STEPS.floorEntry(index).getValue();
  }

  /** {@code even} on even sessions, {@code odd} on odd ones. */
  private static IntFunction<String> alternating(String even, String odd) {
    return index -> index % 2 == 0 ? even : odd;
  }

  private static ClosingPrices series(IntFunction<String> price, Set<Integer> missing) {
    List<DailyClose> closes =
        IntStream.range(0, YearWindow.SESSIONS)
            .filter(index -> !missing.contains(index))
            .mapToObj(index -> close(WINDOW.sessions().get(index), price.apply(index), 1_000L))
            .toList();
    return ClosingPrices.of(WINDOW.sessions(), closes);
  }

  private static ClosingPrices series(IntFunction<String> price) {
    return series(price, Set.of());
  }

  private static DailyClose close(LocalDate day, String price, long volume) {
    return new DailyClose(
        ID, day, new BigDecimal(price), MarketTestData.EUR, PriceSource.EURONEXT, volume, 1);
  }

  /** Indexes {@code from} (inclusive) to {@code to} (exclusive). */
  private static Set<Integer> range(int from, int to) {
    return IntStream.range(from, to).boxed().collect(Collectors.toSet());
  }

  private static BigDecimal value(Indicator indicator) {
    assertThat(indicator.status()).isEqualTo(IndicatorStatus.AVAILABLE);
    return Objects.requireNonNull(indicator.value());
  }

  private static Indicator oneMonth(ClosingPrices year) {
    return IndicatorCalculator.performance(
        year.lastSessions(IndicatorWindow.ONE_MONTH.sessions() + 1));
  }

  @Test
  void windowHas253SessionsEndingAtTheLastSessionWithoutHolidays() {
    assertThat(WINDOW.asOf()).isEqualTo(AS_OF);
    assertThat(WINDOW.sessions()).hasSize(253).doesNotContain(LocalDate.parse("2026-05-01"));
    // 253 weekdays back would start on 2025-10-29; six weekday holidays (25 and 26 December,
    // 1 January, Good Friday, Easter Monday, 1 May) move the start six weekdays earlier.
    assertThat(WINDOW.start()).isEqualTo(LocalDate.parse("2025-10-21"));
  }

  @Test
  void performanceOfEachWindowComparesTheLastCloseWithTheReferenceSession() {
    // 1 month: 150/120 - 1 = 25 %; 3 months: 150/100 - 1 = 50 %; 6 months: 150/80 - 1 = 87.5 %;
    // 1 year: 150/50 - 1 = 200 %.
    IndicatorSet indicators =
        IndicatorCalculator.compute(
            series(IndicatorCalculatorTest::steps), NO_CLOSE, NO_CLOSE, NO_EURONEXT_FILE);

    assertThat(indicators.asOf()).isEqualTo(AS_OF);
    assertThat(indicators.closesOneYear()).isEqualTo(253);
    assertThat(value(indicators.performance(IndicatorWindow.ONE_MONTH))).isEqualTo("25.00");
    assertThat(value(indicators.performance(IndicatorWindow.THREE_MONTHS))).isEqualTo("50.00");
    assertThat(value(indicators.performance(IndicatorWindow.SIX_MONTHS))).isEqualTo("87.50");
    assertThat(value(indicators.performance(IndicatorWindow.ONE_YEAR))).isEqualTo("200.00");
    assertThat(value(indicators.maxDrawdown())).as("never falls").isEqualTo(ZERO);
    assertThat(indicators.msciWorld()).isEqualTo(BenchmarkComparison.INSUFFICIENT_HISTORY);
    assertThat(indicators.cac40()).isEqualTo(BenchmarkComparison.INSUFFICIENT_HISTORY);
  }

  @Test
  void volatilityIsTheAnnualisedSampleStandardDeviationOfDailyReturns() {
    // 252 returns: 248 zeros and the four steps 80/50 - 1 = 0.6, 100/80 - 1 = 0.25,
    // 120/100 - 1 = 0.2, 150/120 - 1 = 0.25. Σr = 1.3, Σr² = 0.525.
    // s² = (Σr² - (Σr)²/252) / 251 = (0.525 - 0.0067063) / 251 = 0.0020649, s = 0.0454412;
    // × √252 = 15.874508 → 0.721359 → 72.14 %.
    Indicator volatility = IndicatorCalculator.volatility(series(IndicatorCalculatorTest::steps));

    assertThat(value(volatility)).isEqualTo("72.14");
  }

  @Test
  void alternatingPricesGiveAKnownVolatility() {
    // 100, 110, 100, 110…: 126 returns of +10 % and 126 of 100/110 - 1 = -1/11. Every return is
    // at ±(0.1 + 1/11)/2 = ±0.0954545 from the mean: s = 0.0954545 × √(252/251) = 0.0956445;
    // × √252 = 1.518309 → 151.83 %.
    Indicator volatility = IndicatorCalculator.volatility(series(alternating(P100, P110)));

    assertThat(value(volatility)).isEqualTo("151.83");
  }

  @Test
  void maxDrawdownIsTheLargestFallFromAHighToALaterLow() {
    ClosingPrices closes = series(index -> FALLS.floorEntry(index).getValue());

    assertThat(value(IndicatorCalculator.maxDrawdown(closes))).isEqualTo("-25.00");
  }

  @Test
  void returnsInPhaseWithTheBenchmarkHaveCorrelationOneAndTheRatioOfAmplitudesAsBeta() {
    // Instrument 100/110 (+10 %, -1/11), MSCI World 100/105 (+5 %, -1/21), same phase: the two
    // returns take two values each, at the same sessions: correlation 1.
    // beta = (0.1 + 1/11) / (0.05 + 1/21) = 0.1909091 / 0.0976190 = 1.955654 → 1.96.
    // Both end at their starting price (index 252 is even): outperformance 0 - 0 = 0.
    ClosingPrices instrument = series(alternating(P100, P110));
    ClosingPrices msciWorld = series(alternating(P100, P105));
    // CAC 40 in opposite phase: 105, 100, 105…
    ClosingPrices cac40 = series(alternating(P105, P100));

    IndicatorSet indicators =
        IndicatorCalculator.compute(instrument, msciWorld, cac40, NO_EURONEXT_FILE);

    assertThat(value(indicators.msciWorld().correlation())).isEqualTo("1.00");
    assertThat(value(indicators.msciWorld().beta())).isEqualTo("1.96");
    assertThat(value(indicators.msciWorld().outperformance())).isEqualTo(ZERO);
    assertThat(value(indicators.cac40().correlation())).isEqualTo("-1.00");
    assertThat(value(indicators.cac40().beta())).isEqualTo("-1.96");
  }

  @Test
  void outperformanceIsTheDifferenceOfTheOneYearPerformances() {
    // Instrument: 50 → 150 (+200 %); benchmark: 100 → 110 (+10 %): 190 percentage points.
    ClosingPrices benchmark = series(i -> i < LAST ? P100 : P110);

    BenchmarkComparison comparison =
        IndicatorCalculator.compare(series(IndicatorCalculatorTest::steps), benchmark);

    assertThat(value(comparison.outperformance())).isEqualTo("190.00");
  }

  @Test
  void outperformanceComparesBothOverTheSessionsWhereBothHaveACloseOnly() {
    // The benchmark goes from 100 to 120 during the first 40 sessions, when the instrument has no
    // close (213 of 253 closes: still enough). Over the common sessions 40 to 252 both stay flat
    // (instrument 50, benchmark 120): 0 - 0 = 0 points, never 0 % - 20 % = -20.
    ClosingPrices instrument = series(i -> "50", range(0, 40));
    ClosingPrices benchmark = series(i -> i < 40 ? P100 : P120);

    BenchmarkComparison comparison = IndicatorCalculator.compare(instrument, benchmark);

    assertThat(value(comparison.outperformance())).isEqualTo(ZERO);
  }

  @Test
  void aPriceThatNeverMovesHasNoCorrelationButAZeroBetaAndVolatility() {
    ClosingPrices flat = series(i -> P100);
    ClosingPrices benchmark = series(alternating(P100, P105));

    BenchmarkComparison comparison = IndicatorCalculator.compare(flat, benchmark);

    assertThat(comparison.correlation()).isEqualTo(Indicator.NOT_COMPUTABLE);
    assertThat(value(comparison.beta())).isEqualTo(ZERO);
    assertThat(value(IndicatorCalculator.volatility(flat))).isEqualTo(ZERO);
    assertThat(IndicatorCalculator.compare(benchmark, flat).beta())
        .as("constant benchmark")
        .isEqualTo(Indicator.NOT_COMPUTABLE);
  }

  @Test
  void eightyPercentOfTheSessionsOfAWindowAreEnoughFromTheFirstCloseOfTheWindow() {
    // One month = 22 closes (reference session 231 + sessions 232 to 252): 80 % of 22 = 17.6, so
    // 18 are needed: 4 missing is fine, 5 is not. Prices: 100 until session 234, 120 until 251,
    // 150 on the last. Sessions 231 to 234 missing: the window starts at its first close (120,
    // session 235), never at a close before the window (100): 150/120 - 1 = 25 %.
    int reference = start(IndicatorWindow.ONE_MONTH);
    IntFunction<String> prices = i -> i < reference + 4 ? P100 : i < LAST ? P120 : "150";

    assertThat(value(oneMonth(series(prices, range(reference, reference + 4))))).isEqualTo("25.00");
    assertThat(oneMonth(series(prices, range(reference, reference + 5))))
        .isEqualTo(Indicator.INSUFFICIENT_HISTORY);
  }

  @Test
  void aMissingLastSessionEndsTheWindowAtTheLastClose() {
    // Same prices, only the last session (150) missing: from 100 (session 231) to 120 (session
    // 251), 120/100 - 1 = 20 %.
    int reference = start(IndicatorWindow.ONE_MONTH);
    IntFunction<String> prices = i -> i < reference + 4 ? P100 : i < LAST ? P120 : "150";

    assertThat(value(oneMonth(series(prices, Set.of(LAST))))).isEqualTo("20.00");
  }

  @Test
  void oneYearIndicatorsNeed203OfThe253Closes() {
    // 80 % of 253 = 202.4 → 203 closes: 50 missing is fine, 51 is not. Missing in the middle.
    ClosingPrices fiftyMissing = series(alternating(P100, P110), range(100, 150));
    ClosingPrices fiftyOneMissing = series(alternating(P100, P110), range(100, 151));
    ClosingPrices benchmark = series(alternating(P100, P105));

    IndicatorSet enough =
        IndicatorCalculator.compute(fiftyMissing, benchmark, benchmark, NO_EURONEXT_FILE);
    IndicatorSet notEnough =
        IndicatorCalculator.compute(fiftyOneMissing, benchmark, benchmark, NO_EURONEXT_FILE);

    assertThat(enough.closesOneYear()).isEqualTo(203);
    assertThat(enough.volatility().available()).isTrue();
    assertThat(enough.msciWorld().correlation().available()).isTrue();
    assertThat(notEnough.closesOneYear()).isEqualTo(202);
    assertThat(notEnough.volatility()).isEqualTo(Indicator.INSUFFICIENT_HISTORY);
    assertThat(notEnough.maxDrawdown()).isEqualTo(Indicator.INSUFFICIENT_HISTORY);
    assertThat(notEnough.performance(IndicatorWindow.ONE_YEAR))
        .isEqualTo(Indicator.INSUFFICIENT_HISTORY);
    assertThat(notEnough.msciWorld()).isEqualTo(BenchmarkComparison.INSUFFICIENT_HISTORY);
    assertThat(notEnough.performance(IndicatorWindow.ONE_MONTH).available())
        .as("the recent windows are complete")
        .isTrue();
  }

  @Test
  void aMissingSessionRemovesTheTwoReturnsAroundIt() {
    // 100, 110, (missing), 121, 133.1: returns +10 %, unknown, unknown, +10 %.
    List<LocalDate> sessions = WINDOW.sessions().subList(0, 5);
    ClosingPrices closes =
        ClosingPrices.of(
            sessions,
            List.of(
                close(sessions.get(0), P100, 1),
                close(sessions.get(1), P110, 1),
                close(sessions.get(3), "121", 1),
                close(sessions.get(4), "133.1", 1)));

    DailyReturns returns = closes.dailyReturns();

    assertThat(returns.count()).isEqualTo(2);
    assertThat(returns.standardDeviation().getAsDouble())
        .as("two identical returns of +10 %, never a two-session +21 %")
        .isLessThan(1e-12);
  }

  @Test
  void averageDailyTradedAmountUsesEuronextVolumesOfTheLastThreeMonths() {
    // 63 sessions of close 10 × volume 1,000 = 10,000 → 10000.00. An EODHD volume (other
    // definition) and a close without volume are not amounts, even on a session whose Euronext
    // files were collected: with 12 sessions left (51 of 63, ≥ 80 % of 63 = 50.4) the mean is
    // still 10,000; with 13 left it is insufficient.
    List<LocalDate> sessions = WINDOW.sessions().subList(LAST - 62, LAST + 1);
    IntFunction<DailyClose> euronext = i -> close(sessions.get(i), "10", 1_000L);
    ClosingPrices complete =
        ClosingPrices.of(sessions, IntStream.range(0, 63).mapToObj(euronext).toList());
    ClosingPrices twelveUnknown =
        ClosingPrices.of(
            sessions,
            IntStream.range(0, 63)
                .mapToObj(i -> i < 12 ? eodhd(sessions.get(i)) : euronext.apply(i))
                .toList());
    ClosingPrices thirteenUnknown =
        ClosingPrices.of(
            sessions,
            IntStream.range(0, 63)
                .mapToObj(i -> i < 13 ? withoutVolume(sessions.get(i)) : euronext.apply(i))
                .toList());
    Set<LocalDate> collected = Set.copyOf(sessions);

    assertThat(value(IndicatorCalculator.averageDailyTradedAmount(complete, NO_EURONEXT_FILE)))
        .isEqualTo("10000.00");
    assertThat(value(IndicatorCalculator.averageDailyTradedAmount(twelveUnknown, collected)))
        .isEqualTo("10000.00");
    assertThat(IndicatorCalculator.averageDailyTradedAmount(thirteenUnknown, collected))
        .isEqualTo(Indicator.INSUFFICIENT_HISTORY);
  }

  @Test
  void aCollectedSessionWithoutTradeCountsAsZero() {
    // 50 sessions traded (10 × 1,000 = 10,000) and 13 sessions without any close. When the
    // Euronext files of those 13 sessions were collected, the instrument did not trade:
    // 50 × 10,000 / 63 = 7,936.507… → 7936.51 (never 10,000, which would overstate its liquidity).
    // When they were not collected (application off), they are unknown: 50 < 51, insufficient.
    List<LocalDate> sessions = WINDOW.sessions().subList(LAST - 62, LAST + 1);
    ClosingPrices thinlyTraded =
        ClosingPrices.of(
            sessions,
            IntStream.range(13, 63).mapToObj(i -> close(sessions.get(i), "10", 1_000L)).toList());

    assertThat(
            value(IndicatorCalculator.averageDailyTradedAmount(thinlyTraded, Set.copyOf(sessions))))
        .isEqualTo("7936.51");
    assertThat(IndicatorCalculator.averageDailyTradedAmount(thinlyTraded, NO_EURONEXT_FILE))
        .isEqualTo(Indicator.INSUFFICIENT_HISTORY);
  }

  @Test
  void averageDailyTradedAmountIsTheMeanOfCloseTimesVolume() {
    // 2 sessions: 10.5 × 100 = 1,050 and 20 × 300 = 6,000 → (1,050 + 6,000) / 2 = 3,525.
    List<LocalDate> sessions = WINDOW.sessions().subList(0, 2);
    ClosingPrices closes =
        ClosingPrices.of(
            sessions,
            List.of(close(sessions.get(0), "10.5", 100), close(sessions.get(1), "20", 300)));

    assertThat(value(IndicatorCalculator.averageDailyTradedAmount(closes, NO_EURONEXT_FILE)))
        .isEqualTo("3525.00");
  }

  @Test
  void closesOutsideTheSessionsAreIgnored() {
    List<LocalDate> sessions = WINDOW.sessions().subList(0, 2);
    LocalDate outside = LocalDate.parse("2020-01-02");
    ClosingPrices closes = ClosingPrices.of(sessions, List.of(close(outside, "10", 1)));

    assertThat(closes.knownCount()).isZero();
    assertThat(closes.on(outside)).isEmpty();
    assertThat(closes.tradedAmountOn(outside)).isEmpty();
  }

  private static DailyClose eodhd(LocalDate day) {
    return new DailyClose(
        ID, day, BigDecimal.TEN, MarketTestData.EUR, PriceSource.EODHD, 1_000L, null);
  }

  private static DailyClose withoutVolume(LocalDate day) {
    return new DailyClose(ID, day, BigDecimal.TEN, MarketTestData.EUR, PriceSource.EURONEXT);
  }
}
