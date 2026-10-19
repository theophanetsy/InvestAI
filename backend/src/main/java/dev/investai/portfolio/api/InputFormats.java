package dev.investai.portfolio.api;

import java.math.BigDecimal;

/**
 * Formats of the request fields. Amounts travel as decimal strings ({@code "1500.50"}), never as
 * floating-point JSON numbers: the browser never turns them into binary floats (spec 2.3, criterion
 * 10).
 */
final class InputFormats {

  /** Amount in euros, positive or zero, at most 2 decimal places. */
  static final String AMOUNT = "^\\d{1,13}(\\.\\d{1,2})?$";

  /** Price in euros, at most 4 decimal places (scale of the market module). */
  static final String PRICE = "^\\d{1,15}(\\.\\d{1,4})?$";

  /** Text on one line: no control character. */
  static final String SINGLE_LINE = "^[^\\x00-\\x1F\\x7F]*$";

  /** Text over several lines: line feeds and tabs only, no other control character. */
  static final String MULTILINE = "^[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]*$";

  private InputFormats() {}

  /**
   * Parses a string already checked by {@link #AMOUNT} or {@link #PRICE}.
   *
   * @param text the decimal string
   * @return the exact value
   * @throws IllegalArgumentException when it is not a number (never after validation)
   */
  static BigDecimal parse(String text) {
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Not a decimal number", ex);
    }
  }

  /**
   * Formats an amount for a response.
   *
   * @param value the amount
   * @return its plain decimal string, e.g. {@code "1500.50"}
   */
  static String format(BigDecimal value) {
    return value.toPlainString();
  }
}
