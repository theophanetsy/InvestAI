package dev.investai.market.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Port: the rate-limited market data API (EODHD free plan, ADR-0009) providing reference data and
 * history. Only scheduled jobs call it; every call costs one unit of the daily budget.
 */
public interface MarketDataProvider {

  /**
   * Tells whether the provider is configured (API key present).
   *
   * @return {@code false} when its features are disabled
   */
  boolean isEnabled();

  /**
   * Downloads the reference data of Euronext Paris (one call).
   *
   * @return the usable entries and the number of ignored ones
   * @throws MarketDataUnavailableException on any failure
   */
  ReferenceData referenceData();

  /**
   * Downloads raw daily closes of an instrument (one call).
   *
   * @param instrument the instrument
   * @param from first session, inclusive
   * @param to last session, inclusive
   * @return closes by ascending date; sessions without trade may be missing
   * @throws MarketDataUnavailableException on any failure
   */
  List<HistoricalClose> dailyCloses(Instrument instrument, LocalDate from, LocalDate to);
}
