package dev.investai.market.infrastructure;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Reads decimal numbers published by the providers. Parsing a {@link BigDecimal} is quadratic in
 * its length: a forged number of thousands of digits is never parsed (security review, phase 2.2).
 */
final class Decimals {

  /** Longer than any price, quantity or volume of the providers (a long has 19 digits). */
  static final int MAX_LENGTH = 32;

  private Decimals() {}

  /**
   * Reads a number that may be missing or unusable without failing the line or the call.
   *
   * @param text the published text
   * @return the number, empty when missing, too long or not a number
   */
  static Optional<BigDecimal> lenient(String text) {
    if (text.length() > MAX_LENGTH) {
      return Optional.empty();
    }
    try {
      return Optional.of(new BigDecimal(text));
    } catch (NumberFormatException ex) {
      return Optional.empty();
    }
  }
}
