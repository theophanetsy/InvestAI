package dev.investai.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

/**
 * Closing price of an instrument for one session, as traded (never adjusted, ADR-0009).
 *
 * @param instrumentId the instrument
 * @param tradingDate the session
 * @param close the price, at scale 4
 * @param currency the price currency
 * @param source where it comes from
 */
public record DailyClose(
    InstrumentId instrumentId,
    LocalDate tradingDate,
    BigDecimal close,
    Currency currency,
    PriceSource source) {

  /** Checks the price is storable as is: positive, at most 4 decimal places. */
  public DailyClose {
    close =
        Prices.exact(close)
            .orElseThrow(() -> new IllegalArgumentException("Close not storable at scale 4"));
  }

  /**
   * Key of the close: one per instrument and session.
   *
   * @return the key
   */
  public PriceKey key() {
    return new PriceKey(instrumentId, tradingDate);
  }
}
