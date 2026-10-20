package dev.investai.market.domain;

/** Whether an indicator has a value, and why not. */
public enum IndicatorStatus {
  /** Computed: the value is given. */
  AVAILABLE,
  /**
   * Fewer than 80 % of the sessions of the window have a close (of the instrument, or of the
   * benchmark for a comparison): never an approximate value.
   */
  INSUFFICIENT_HISTORY,
  /**
   * Enough history but undefined, e.g. a correlation with a price that never moved, or a share of a
   * total that is zero.
   */
  NOT_COMPUTABLE
}
