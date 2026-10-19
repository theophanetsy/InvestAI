package dev.investai.portfolio.api;

import java.math.BigDecimal;

/**
 * Formats of the request fields. Amounts travel as decimal strings ({@code "1500.50"}), never as
 * floating-point JSON numbers: the browser never turns them into binary floats (spec 2.3, criterion
 * 10). Every check is an anchored pattern, linear and stopped early on a long input: never
 * {@code @DecimalMin} on a string, which parses the whole text first, in quadratic time.
 */
final class InputFormats {

  /** Amount in euros, positive or zero, at most 2 decimal places. */
  static final String AMOUNT = "^\\d{1,13}(\\.\\d{1,2})?$";

  /** Strictly positive amount in euros, at most 2 decimal places. */
  static final String POSITIVE_AMOUNT = "^(?!0+(\\.0+)?$)\\d{1,13}(\\.\\d{1,2})?$";

  /** Strictly positive price in euros, at most 4 decimal places (scale of the market module). */
  static final String PRICE = "^(?!0+(\\.0+)?$)\\d{1,15}(\\.\\d{1,4})?$";

  /**
   * Text on one line, without anything invisible that the advice agent would read but the owner
   * would not see: control (Cc, C1 included), format (Cf: bidi, zero width, tags), private use
   * (Co), unassigned (Cn), line and paragraph separators (Zl, Zp). A single character class: no
   * recursion in the regex engine, whatever the length.
   */
  static final String SINGLE_LINE = "^[^\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}\\p{Zl}\\p{Zp}]*$";

  /** Like {@link #SINGLE_LINE}, plus tabs and line breaks. */
  static final String MULTILINE = "^[\\t\\n\\r[^\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}\\p{Zl}\\p{Zp}]]*$";

  private InputFormats() {}

  /**
   * Parses a string already checked by one of the decimal patterns.
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
