package dev.investai.market.domain;

/** Where a daily close comes from (ADR-0009). An Euronext close always wins over EODHD. */
public enum PriceSource {
  /** Deferred MiFIR trade files published by Euronext: the official source. */
  EURONEXT,
  /** EODHD free plan: history and catch-up of missed sessions. */
  EODHD
}
