package dev.investai.portfolio.api;

import dev.investai.portfolio.domain.PortfolioLine;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * New declared values of a line; its instrument does not change.
 *
 * @param quantity number of units held, a positive integer
 * @param averagePrice average purchase price in euros: decimal string, positive, at most 4 decimal
 *     places; absent to clear it
 */
public record PortfolioLineUpdateRequest(
    @Min(1) @Max(PortfolioLine.MAX_QUANTITY) long quantity,
    @Pattern(regexp = InputFormats.PRICE)
        @Schema(example = "150.1234", pattern = InputFormats.PRICE)
        @Nullable String averagePrice) {

  @Nullable BigDecimal averagePriceValue() {
    return averagePrice == null ? null : InputFormats.parse(averagePrice);
  }
}
