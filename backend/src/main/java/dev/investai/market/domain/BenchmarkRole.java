package dev.investai.market.domain;

/** What a configured benchmark ETF is used for (spec 3.1, criterion 27). */
public enum BenchmarkRole {
  /** The MSCI World ETF the indicators compare every instrument with. */
  MSCI_WORLD_REFERENCE,
  /** The CAC 40 ETF the indicators compare every instrument with. */
  CAC40_REFERENCE,
  /** Another configured benchmark, not used by the comparisons. */
  OTHER
}
