package dev.investai.market.domain;

/** Kind of listed instrument. */
public enum InstrumentType {
  /** Share. */
  STOCK,
  /** Exchange-traded fund. */
  ETF,
  /** Other fund. */
  FUND,
  /** Anything else (warrants, preferred shares...). */
  OTHER
}
