package dev.investai.assistant.infrastructure;

import dev.investai.market.domain.DailyClose;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A raw close with its session, in the currency of the instrument.
 *
 * @param price plain decimal string
 * @param date the session ({@code YYYY-MM-DD}, Euronext calendar)
 */
public record LastClose(String price, LocalDate date) {

  static LastClose of(DailyClose close) {
    return of(close.close(), close.tradingDate());
  }

  static LastClose of(BigDecimal price, LocalDate date) {
    return new LastClose(price.toPlainString(), date);
  }
}
