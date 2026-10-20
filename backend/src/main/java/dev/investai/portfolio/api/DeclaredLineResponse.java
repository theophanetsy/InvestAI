package dev.investai.portfolio.api;

import dev.investai.portfolio.domain.PortfolioLine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A line as declared, without valuation ({@code getPortfolio} gives the valuation).
 *
 * @param id identifier of the line
 * @param instrumentId the instrument
 * @param quantity number of units held
 * @param averagePrice average purchase price in euros, decimal string at scale 4; absent when not
 *     given
 * @param updatedAt last change
 */
public record DeclaredLineResponse(
    UUID id,
    UUID instrumentId,
    long quantity,
    @Schema(example = "150.1234", pattern = "^\\d+\\.\\d{4}$") @Nullable String averagePrice,
    Instant updatedAt) {

  static DeclaredLineResponse from(PortfolioLine line) {
    BigDecimal averagePrice = line.averagePrice();
    return new DeclaredLineResponse(
        line.id().value(),
        line.instrumentId().value(),
        line.quantity(),
        averagePrice == null ? null : InputFormats.format(averagePrice),
        line.updatedAt());
  }
}
