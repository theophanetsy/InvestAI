package dev.investai.market.domain;

/** Segment of the Euronext deferred trade files. */
public enum TradeFileSegment {
  /** Shares (regulated market and Euronext Growth). */
  EQUITIES,
  /** ETFs and funds. */
  ETFS_FUNDS
}
