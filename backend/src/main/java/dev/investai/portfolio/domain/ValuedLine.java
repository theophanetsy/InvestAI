package dev.investai.portfolio.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * A line of the valuation. Without a close every computed field is {@code null}: the line is
 * flagged "price unavailable", never valued at zero.
 *
 * @param line the declared line
 * @param quote the close used, {@code null} when none is known
 * @param value quantity times the close, in euros at scale 2
 * @param unrealizedGain value minus quantity times the average price, at scale 2; {@code null}
 *     without average price
 * @param unrealizedGainPercent close compared with the average price, in percent at scale 2
 * @param weightPercent share of the total (cash included), in percent at scale 2; {@code null} when
 *     the total is zero
 */
public record ValuedLine(
    PortfolioLine line,
    @Nullable Quote quote,
    @Nullable BigDecimal value,
    @Nullable BigDecimal unrealizedGain,
    @Nullable BigDecimal unrealizedGainPercent,
    @Nullable BigDecimal weightPercent) {

  /**
   * Tells whether the line could be valued.
   *
   * @return {@code true} when a close is known
   */
  public boolean priced() {
    return quote != null;
  }
}
