package dev.investai.market.api;

import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.PriceSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Raw closing price of a session ("as traded", not adjusted), with its activity when known.
 *
 * @param date the session
 * @param close the price as a decimal string with 4 decimal places, never a floating-point number
 * @param currency price currency (ISO 4217)
 * @param source where it comes from: Euronext files, or EODHD for history and catch-up
 * @param volume units traded in the session; absent when unknown. Euronext: central order book
 *     trades on the listing venue; EODHD: as published by the provider
 * @param tradeCount number of trades in the session (Euronext only); absent when unknown
 */
public record DailyCloseResponse(
    LocalDate date,
    @Schema(example = "173.1600", pattern = "^\\d+\\.\\d{4}$") String close,
    String currency,
    PriceSource source,
    @Schema(example = "812345", minimum = "0") @Nullable Long volume,
    @Schema(example = "4321", minimum = "0") @Nullable Integer tradeCount) {

  static DailyCloseResponse from(DailyClose close) {
    return new DailyCloseResponse(
        close.tradingDate(),
        close.close().toPlainString(),
        close.currency().getCurrencyCode(),
        close.source(),
        close.volume(),
        close.tradeCount());
  }
}
