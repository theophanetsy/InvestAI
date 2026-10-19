package dev.investai.market.domain;

import java.math.BigDecimal;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Storage rule of volumes: a whole, non-negative number of units ({@code bigint}). */
public final class Volumes {

  private Volumes() {}

  /**
   * Converts a quantity read from a provider into a number of units, without rounding.
   *
   * @param quantity the quantity as published, {@code null} when missing
   * @return the number of units, or empty when the quantity is missing, negative, fractional or
   *     beyond a {@code long}
   */
  public static Optional<Long> units(@Nullable BigDecimal quantity) {
    if (quantity == null || quantity.signum() < 0) {
      return Optional.empty();
    }
    try {
      return Optional.of(quantity.longValueExact());
    } catch (ArithmeticException ex) {
      // Fractional part or beyond a long: unknown rather than rounded.
      return Optional.empty();
    }
  }
}
