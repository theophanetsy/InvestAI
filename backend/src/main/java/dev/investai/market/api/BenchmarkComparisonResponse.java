package dev.investai.market.api;

import dev.investai.market.domain.BenchmarkComparison;

/**
 * An instrument compared with a benchmark ETF over one year.
 *
 * @param outperformance one-year performance minus that of the benchmark, in percentage points
 * @param correlation correlation of the daily returns, between -1 and 1
 * @param beta sensitivity of the daily returns to those of the benchmark
 */
public record BenchmarkComparisonResponse(
    IndicatorResponse outperformance, IndicatorResponse correlation, IndicatorResponse beta) {

  static BenchmarkComparisonResponse from(BenchmarkComparison comparison) {
    return new BenchmarkComparisonResponse(
        IndicatorResponse.from(comparison.outperformance()),
        IndicatorResponse.from(comparison.correlation()),
        IndicatorResponse.from(comparison.beta()));
  }
}
