package dev.investai.market.domain;

import java.math.BigDecimal;
import java.util.Optional;

/** Storage rules of prices: {@code numeric(19, 4)}, never rounded. */
public final class Prices {

  /** Decimal places kept in the database. */
  public static final int SCALE = 4;

  private static final int MAX_INTEGER_DIGITS = 15;

  private Prices() {}

  /**
   * Brings a price to {@link #SCALE} decimal places without rounding.
   *
   * @param price a price read from a provider
   * @return the price at scale 4, or empty when it is not positive, has more than 4 significant
   *     decimal places or does not fit in {@code numeric(19, 4)}
   */
  public static Optional<BigDecimal> exact(BigDecimal price) {
    BigDecimal stripped = price.stripTrailingZeros();
    if (stripped.signum() <= 0
        || stripped.scale() > SCALE
        || stripped.precision() - stripped.scale() > MAX_INTEGER_DIGITS) {
      return Optional.empty();
    }
    return Optional.of(stripped.setScale(SCALE));
  }
}
