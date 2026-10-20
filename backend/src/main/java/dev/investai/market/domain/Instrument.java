package dev.investai.market.domain;

import java.time.Instant;
import java.util.Currency;
import org.jspecify.annotations.Nullable;

/**
 * A listed instrument of the local reference data.
 *
 * @param id identifier
 * @param listing ISIN and venue, unique
 * @param ticker exchange ticker, e.g. {@code AI}
 * @param name instrument name
 * @param type kind of instrument
 * @param currency trading currency
 * @param trackedSince when the user started following it, {@code null} when not followed
 * @param historyLoaded whether one year of closes has been loaded
 * @param peaEligibility PEA eligibility: the owner's correction, or the rule
 */
public record Instrument(
    InstrumentId id,
    Listing listing,
    String ticker,
    String name,
    InstrumentType type,
    Currency currency,
    @Nullable Instant trackedSince,
    boolean historyLoaded,
    PeaEligibility peaEligibility) {

  /**
   * Tells whether the instrument is followed: only followed instruments get one year of EODHD
   * history and the catch-up of missed sessions; every instrument gets the Euronext closes.
   *
   * @return {@code true} when tracked
   */
  public boolean tracked() {
    return trackedSince != null;
  }
}
