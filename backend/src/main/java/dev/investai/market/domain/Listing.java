package dev.investai.market.domain;

/**
 * Where an instrument is quoted: the pivot identifier of the market module (ADR-0009). An ISIN has
 * several listings (CW8 on XPAR and ETFP, Air Liquide in Paris, Xetra, London...).
 *
 * @param isin the instrument
 * @param mic the trading venue
 */
public record Listing(Isin isin, Mic mic) {

  @Override
  public String toString() {
    return isin + "@" + mic;
  }
}
