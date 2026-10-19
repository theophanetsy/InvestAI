package dev.investai.market.domain;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * International Securities Identification Number (ISO 6166): two-letter country code, nine
 * alphanumeric characters and a check digit computed with the Luhn algorithm.
 *
 * @param value the twelve characters, upper case
 */
public record Isin(String value) {

  private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");
  private static final int LETTER_OFFSET = 10;
  private static final int RADIX = 10;
  private static final int MAX_DIGIT = 9;

  /** Validates the value; the message never echoes it (it may come from a file or a client). */
  public Isin {
    if (!isValid(value)) {
      throw new IllegalArgumentException("Invalid ISIN");
    }
  }

  /**
   * Tells whether a value is a well-formed ISIN with a correct check digit.
   *
   * @param value any value
   * @return {@code true} when {@link #Isin(String)} accepts it
   */
  public static boolean isValid(@Nullable String value) {
    return value != null && FORMAT.matcher(value).matches() && hasValidCheckDigit(value);
  }

  /** Letters become two digits (A = 10 … Z = 35), then the Luhn sum must be a multiple of 10. */
  private static boolean hasValidCheckDigit(String value) {
    StringBuilder digits = new StringBuilder(2 * value.length());
    for (int i = 0; i < value.length(); i++) {
      char character = value.charAt(i);
      digits.append(
          Character.isLetter(character) ? character - 'A' + LETTER_OFFSET : character - '0');
    }
    int sum = 0;
    boolean doubled = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int digit = digits.charAt(i) - '0';
      if (doubled) {
        digit *= 2;
        digit = digit > MAX_DIGIT ? digit - MAX_DIGIT : digit;
      }
      sum += digit;
      doubled = !doubled;
    }
    return sum % RADIX == 0;
  }

  @Override
  public String toString() {
    return value;
  }
}
