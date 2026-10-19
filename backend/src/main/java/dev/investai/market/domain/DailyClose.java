package dev.investai.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import org.jspecify.annotations.Nullable;

/**
 * Closing price of an instrument for one session, as traded (never adjusted, ADR-0009), with the
 * activity of the session when the source gives it.
 *
 * @param instrumentId the instrument
 * @param tradingDate the session
 * @param close the price, at scale 4
 * @param currency the price currency
 * @param source where it comes from
 * @param volume units traded in the session, {@code null} when unknown; Euronext: central order
 *     book trades on the listing venue, EODHD: as published by the provider
 * @param tradeCount number of trades in the session (Euronext only, same trades as the volume),
 *     {@code null} when unknown
 */
public record DailyClose(
    InstrumentId instrumentId,
    LocalDate tradingDate,
    BigDecimal close,
    Currency currency,
    PriceSource source,
    @Nullable Long volume,
    @Nullable Integer tradeCount) {

  /** Checks the price is storable as is (positive, at most 4 decimal places) and the activity. */
  public DailyClose {
    close =
        Prices.exact(close)
            .orElseThrow(() -> new IllegalArgumentException("Close not storable at scale 4"));
    if (volume != null && volume < 0) {
      throw new IllegalArgumentException("Negative volume");
    }
    if (tradeCount != null && tradeCount < 0) {
      throw new IllegalArgumentException("Negative trade count");
    }
  }

  /**
   * Creates a close without activity (volume and trade count unknown).
   *
   * @param instrumentId the instrument
   * @param tradingDate the session
   * @param close the price, at most 4 decimal places
   * @param currency the price currency
   * @param source where it comes from
   */
  public DailyClose(
      InstrumentId instrumentId,
      LocalDate tradingDate,
      BigDecimal close,
      Currency currency,
      PriceSource source) {
    this(instrumentId, tradingDate, close, currency, source, null, null);
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
