package dev.investai.portfolio.domain;

import dev.investai.market.domain.InstrumentId;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A line of the declared PEA, copied from what the broker shows (spec 2.3, criterion 3): no
 * transaction history, no recomputed average price.
 *
 * @param id identifier
 * @param instrumentId the instrument (market module); one line per instrument
 * @param quantity number of units held, positive
 * @param averagePrice average purchase price in euros shown by the broker, at most 4 decimal
 *     places, {@code null} when not given
 * @param updatedAt last change of the line
 */
public record PortfolioLine(
    PortfolioLineId id,
    InstrumentId instrumentId,
    long quantity,
    @Nullable BigDecimal averagePrice,
    Instant updatedAt) {

  /** Maximum quantity of a line, far above any real holding. */
  public static final long MAX_QUANTITY = 1_000_000_000L;

  /** Checks the quantity and the average price (positive, at most 4 decimal places). */
  public PortfolioLine {
    if (quantity <= 0 || quantity > MAX_QUANTITY) {
      throw new IllegalArgumentException("Quantity out of range");
    }
    if (averagePrice != null) {
      if (averagePrice.signum() <= 0) {
        throw new IllegalArgumentException("Average price not positive");
      }
      averagePrice = Amounts.exact(averagePrice, Amounts.PRICE_SCALE);
    }
  }

  /**
   * The same line with other declared values.
   *
   * @param newQuantity number of units held
   * @param newAveragePrice average purchase price, {@code null} when not given
   * @param at time of the change
   * @return the updated line
   */
  public PortfolioLine update(long newQuantity, @Nullable BigDecimal newAveragePrice, Instant at) {
    return new PortfolioLine(id, instrumentId, newQuantity, newAveragePrice, at);
  }
}
