package dev.investai.market.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Indicators of an instrument on its raw closes (spec 2.4, criteria 1 to 5). Amounts and ratios of
 * prices are {@link BigDecimal}; the statistics of returns (volatility, correlation, beta) are
 * {@code double}, then rounded half-even to the scale they are exposed at. Every window needs at
 * least 80 % of its sessions, otherwise the indicator is {@link Indicator#INSUFFICIENT_HISTORY}.
 */
public final class IndicatorCalculator {

  /** Sessions of the three-month window of the liquidity. */
  public static final int LIQUIDITY_SESSIONS = IndicatorWindow.THREE_MONTHS.sessions();

  /** Decimal places of a percentage. */
  public static final int PERCENT_SCALE = 2;

  /** Decimal places of a correlation or a beta. */
  public static final int COEFFICIENT_SCALE = 2;

  /** Decimal places of an amount. */
  public static final int AMOUNT_SCALE = 2;

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final double PERCENT = 100.0;
  private static final MathContext RATIO = MathContext.DECIMAL64;
  private static final double SQRT_TRADING_DAYS = Math.sqrt(IndicatorWindow.ONE_YEAR.sessions());

  private IndicatorCalculator() {}

  /**
   * Computes every indicator of an instrument.
   *
   * @param year closes of the instrument on a {@link YearWindow}
   * @param msciWorld closes of the MSCI World ETF on the same sessions
   * @param cac40 closes of the CAC 40 ETF on the same sessions
   * @param euronextSessions sessions whose two Euronext files were collected
   * @return the indicators, as of the last session of the window
   */
  public static IndicatorSet compute(
      ClosingPrices year,
      ClosingPrices msciWorld,
      ClosingPrices cac40,
      Set<LocalDate> euronextSessions) {
    YearWindow window = new YearWindow(year.sessions());
    Map<IndicatorWindow, Indicator> performance = new EnumMap<>(IndicatorWindow.class);
    for (IndicatorWindow period : IndicatorWindow.values()) {
      performance.put(period, performance(year.lastSessions(period.sessions() + 1)));
    }
    return new IndicatorSet(
        window.asOf(),
        year.knownCount(),
        performance,
        volatility(year),
        maxDrawdown(year),
        compare(year, msciWorld),
        compare(year, cac40),
        averageDailyTradedAmount(year.lastSessions(LIQUIDITY_SESSIONS), euronextSessions));
  }

  /**
   * Change between the first and the last known values of a window, in percent at scale 2 (the
   * first value is the reference session unless it is missing).
   *
   * @param window the values of the window
   * @return the performance
   */
  public static Indicator performance(ClosingPrices window) {
    if (!window.sufficient()) {
      return Indicator.INSUFFICIENT_HISTORY;
    }
    return Indicator.of(percent(change(window.knownValues())));
  }

  /**
   * Annualised volatility: sample standard deviation of the daily returns × √252, in percent at
   * scale 2.
   *
   * @param window the values of the window
   * @return the volatility
   */
  public static Indicator volatility(ClosingPrices window) {
    if (!window.sufficient()) {
      return Indicator.INSUFFICIENT_HISTORY;
    }
    OptionalDouble deviation = window.dailyReturns().standardDeviation();
    return deviation.isPresent()
        ? Indicator.of(round(deviation.getAsDouble() * SQRT_TRADING_DAYS * PERCENT, PERCENT_SCALE))
        : Indicator.NOT_COMPUTABLE;
  }

  /**
   * Largest fall from a high to a later low of the known values, in percent at scale 2: zero when
   * the values never fall, negative otherwise.
   *
   * @param window the values of the window
   * @return the maximum drawdown
   */
  public static Indicator maxDrawdown(ClosingPrices window) {
    if (!window.sufficient()) {
      return Indicator.INSUFFICIENT_HISTORY;
    }
    List<BigDecimal> values = window.knownValues();
    BigDecimal worst = BigDecimal.ZERO;
    BigDecimal peak = values.getFirst();
    for (BigDecimal value : values) {
      peak = peak.max(value);
      BigDecimal drawdown = value.subtract(peak).divide(peak, RATIO);
      worst = worst.min(drawdown);
    }
    return Indicator.of(percent(worst));
  }

  /**
   * Compares an instrument with a benchmark over one year. The outperformance compares both
   * performances over the same period: from the first to the last session where both have a close.
   *
   * @param year closes of the instrument
   * @param benchmark closes of the benchmark on the same sessions
   * @return outperformance, correlation and beta
   */
  public static BenchmarkComparison compare(ClosingPrices year, ClosingPrices benchmark) {
    if (!year.sufficient() || !benchmark.sufficient()) {
      return BenchmarkComparison.INSUFFICIENT_HISTORY;
    }
    DailyReturns returns = year.dailyReturns();
    DailyReturns benchmarkReturns = benchmark.dailyReturns();
    return new BenchmarkComparison(
        outperformance(year, benchmark),
        coefficient(returns.correlation(benchmarkReturns)),
        coefficient(returns.beta(benchmarkReturns)));
  }

  private static Indicator outperformance(ClosingPrices year, ClosingPrices benchmark) {
    List<LocalDate> common =
        year.sessions().stream()
            .filter(session -> year.on(session).isPresent() && benchmark.on(session).isPresent())
            .toList();
    if (common.size() < 2) {
      return Indicator.NOT_COMPUTABLE;
    }
    return Indicator.of(
        percent(changeBetween(year, common).subtract(changeBetween(benchmark, common))));
  }

  private static BigDecimal changeBetween(ClosingPrices series, List<LocalDate> sessions) {
    return change(
        List.of(
            series.on(sessions.getFirst()).orElseThrow(),
            series.on(sessions.getLast()).orElseThrow()));
  }

  /**
   * Correlation of the daily returns of two series of the same sessions (e.g. two lines of a
   * portfolio, both with a sufficient history), at scale 2.
   *
   * @param first returns of the first series
   * @param second returns of the second series, on the same sessions
   * @return the correlation, not computable when either series never moves
   */
  public static Indicator correlation(DailyReturns first, DailyReturns second) {
    return coefficient(first.correlation(second));
  }

  /**
   * Mean daily traded amount (Euronext close × volume) at scale 2. A session whose two Euronext
   * files were collected but where the instrument has no close counts as zero (no trade), so that a
   * thinly traded instrument is not reported as more liquid than it is; a session with a close of
   * unknown volume, or not collected from Euronext, is unknown. Needs a known amount on at least 80
   * % of the sessions.
   *
   * @param window the window
   * @param euronextSessions sessions whose two Euronext files were collected
   * @return the average amount, in the currency of the instrument
   */
  public static Indicator averageDailyTradedAmount(
      ClosingPrices window, Set<LocalDate> euronextSessions) {
    List<BigDecimal> amounts =
        window.sessions().stream()
            .map(session -> observedAmount(window, session, euronextSessions))
            .flatMap(Optional::stream)
            .toList();
    if (!ClosingPrices.covers(amounts.size(), window.sessions().size())) {
      return Indicator.INSUFFICIENT_HISTORY;
    }
    BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return Indicator.of(
        sum.divide(BigDecimal.valueOf(amounts.size()), AMOUNT_SCALE, RoundingMode.HALF_EVEN));
  }

  private static Optional<BigDecimal> observedAmount(
      ClosingPrices window, LocalDate session, Set<LocalDate> euronextSessions) {
    Optional<BigDecimal> amount = window.tradedAmountOn(session);
    if (amount.isPresent()) {
      return amount;
    }
    boolean withoutTrade = euronextSessions.contains(session) && window.on(session).isEmpty();
    return withoutTrade ? Optional.of(BigDecimal.ZERO) : Optional.empty();
  }

  /**
   * Rounds a statistic half-even.
   *
   * @param value a finite number
   * @param scale decimal places
   * @return the rounded value
   */
  public static BigDecimal round(double value, int scale) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("Not a finite number");
    }
    return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_EVEN);
  }

  /** Relative change from the first to the last value, unrounded. */
  private static BigDecimal change(List<BigDecimal> values) {
    BigDecimal first = values.getFirst();
    return values.getLast().subtract(first).divide(first, RATIO);
  }

  private static BigDecimal percent(BigDecimal ratio) {
    return ratio.multiply(HUNDRED).setScale(PERCENT_SCALE, RoundingMode.HALF_EVEN);
  }

  private static Indicator coefficient(OptionalDouble value) {
    return value.isPresent()
        ? Indicator.of(round(value.getAsDouble(), COEFFICIENT_SCALE))
        : Indicator.NOT_COMPUTABLE;
  }
}
