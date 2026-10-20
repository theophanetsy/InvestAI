package dev.investai.market.domain;

/**
 * An indicator filter of a screening, to count the instruments it leaves out because the indicator
 * is not available (spec 3.1, criterion 25).
 */
public enum ScreeningFilter {
  /** Highest one-year volatility. */
  VOLATILITY,
  /** Largest one-year fall. */
  MAX_DRAWDOWN,
  /** Lowest average daily traded amount, in euros. */
  LIQUIDITY,
  /** Lowest performance over the chosen window. */
  PERFORMANCE
}
