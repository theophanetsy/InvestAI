package dev.investai.portfolio.api;

import dev.investai.portfolio.domain.PortfolioLine;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A new line of the declared PEA, copied from the broker.
 *
 * @param instrumentId the instrument (from {@code searchInstruments}), not yet in the portfolio
 * @param quantity number of units held, a positive integer
 * @param averagePrice average purchase price in euros shown by the broker: decimal string,
 *     positive, at most 4 decimal places; absent when not known
 */
public record PortfolioLineRequest(
    @NotNull UUID instrumentId,
    @Min(1) @Max(PortfolioLine.MAX_QUANTITY) long quantity,
    @Pattern(regexp = InputFormats.PRICE)
        @Schema(example = "150.1234", pattern = InputFormats.PRICE)
        @Nullable String averagePrice) {

  @Nullable BigDecimal averagePriceValue() {
    return averagePrice == null ? null : InputFormats.parse(averagePrice);
  }
}
