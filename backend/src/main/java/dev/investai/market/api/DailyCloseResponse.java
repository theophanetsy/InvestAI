package dev.investai.market.api;

import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.PriceSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Raw closing price of a session ("as traded", not adjusted).
 *
 * @param date the session
 * @param close the price as a decimal string with 4 decimal places, never a floating-point number
 * @param currency price currency (ISO 4217)
 * @param source where it comes from: Euronext files, or EODHD for history and catch-up
 */
public record DailyCloseResponse(
    LocalDate date,
    @Schema(example = "173.1600", pattern = "^\\d+\\.\\d{4}$") String close,
    String currency,
    PriceSource source) {

  static DailyCloseResponse from(DailyClose close) {
    return new DailyCloseResponse(
        close.tradingDate(),
        close.close().toPlainString(),
        close.currency().getCurrencyCode(),
        close.source());
  }
}
