package dev.investai.market.domain;

/**
 * An instrument compared with a benchmark over one year (spec 2.4, criterion 4). Every figure is
 * {@link IndicatorStatus#INSUFFICIENT_HISTORY} when the instrument or the benchmark has fewer than
 * 80 % of the closes of the year.
 *
 * @param outperformance one-year performance of the instrument minus that of the benchmark, in
 *     percentage points at scale 2
 * @param correlation correlation of the daily returns, between -1 and 1 at scale 2
 * @param beta sensitivity of the daily returns to those of the benchmark, at scale 2
 */
public record BenchmarkComparison(Indicator outperformance, Indicator correlation, Indicator beta) {

  /** Not enough closes, of the instrument or of the benchmark. */
  public static final BenchmarkComparison INSUFFICIENT_HISTORY =
      new BenchmarkComparison(
          Indicator.INSUFFICIENT_HISTORY,
          Indicator.INSUFFICIENT_HISTORY,
          Indicator.INSUFFICIENT_HISTORY);
}
