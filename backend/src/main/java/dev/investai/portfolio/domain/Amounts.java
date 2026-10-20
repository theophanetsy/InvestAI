package dev.investai.portfolio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/** Money conventions of the declared PEA: amounts in euros, rounded half-even to the cent. */
public final class Amounts {

  /** The only currency of the declared PEA (Euronext Paris lines, euro cash). */
  public static final Currency EUR = Currency.getInstance("EUR");

  /** Decimal places of an amount in euros. */
  public static final int CENTS = 2;

  /** Decimal places of a price, as stored by the market module. */
  public static final int PRICE_SCALE = 4;

  static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private Amounts() {}

  /**
   * Rounds an amount to the cent, half-even.
   *
   * @param amount any amount
   * @return the amount at scale 2
   */
  public static BigDecimal toCents(BigDecimal amount) {
    return amount.setScale(CENTS, RoundingMode.HALF_EVEN);
  }

  /**
   * Checks that an amount has at most {@code scale} decimal places, without rounding.
   *
   * @param amount the amount
   * @param scale maximum number of decimal places
   * @return the amount at exactly {@code scale} decimal places
   * @throws IllegalArgumentException when it has more decimal places
   */
  static BigDecimal exact(BigDecimal amount, int scale) {
    if (amount.stripTrailingZeros().scale() > scale) {
      throw new IllegalArgumentException("Too many decimal places");
    }
    return amount.setScale(scale, RoundingMode.UNNECESSARY);
  }
}
