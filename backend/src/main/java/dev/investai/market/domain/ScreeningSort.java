package dev.investai.market.domain;

/**
 * Order of the screening results; instruments without the indicator come last, then by name and
 * ISIN.
 */
public enum ScreeningSort {
  /** Best performance over the chosen window first. */
  PERFORMANCE,
  /** Lowest one-year volatility first. */
  VOLATILITY,
  /** Highest average daily traded amount first. */
  LIQUIDITY,
  /** Smallest one-year maximum drawdown first (closest to zero). */
  MAX_DRAWDOWN
}
