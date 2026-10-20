package dev.investai.portfolio.api;

import dev.investai.portfolio.domain.Cash;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Cash available on the PEA.
 *
 * @param amount in euros, decimal string at scale 2
 * @param updatedAt last change
 * @param weightPercent share of the total value, in percent at scale 2; absent when the total is
 *     zero or outside a valuation
 */
public record CashResponse(
    @Schema(example = "350.00", pattern = "^\\d+\\.\\d{2}$") String amount,
    Instant updatedAt,
    @Schema(example = "12.50") @Nullable String weightPercent) {

  static CashResponse from(Cash cash, @Nullable BigDecimal weightPercent) {
    return new CashResponse(
        InputFormats.format(cash.amount()),
        cash.updatedAt(),
        weightPercent == null ? null : InputFormats.format(weightPercent));
  }
}
