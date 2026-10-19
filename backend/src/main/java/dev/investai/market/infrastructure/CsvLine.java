package dev.investai.market.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Splits one CSV line (RFC 4180 without line breaks in fields): comma separator, optional double
 * quotes, {@code ""} for a quote inside a quoted field. Enough for the MiFIR files, which have no
 * comma in their values; no dependency needed.
 */
final class CsvLine {

  private static final char SEPARATOR = ',';
  private static final char QUOTE = '"';
  private static final int MALFORMED = -1;

  private CsvLine() {}

  /**
   * Parses a line.
   *
   * @param line the line, without its line terminator
   * @return the fields, or empty when the quotes are malformed
   */
  static Optional<String[]> parse(String line) {
    List<String> fields = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    int position = 0;
    while (position != MALFORMED) {
      position =
          position < line.length() && line.charAt(position) == QUOTE
              ? readQuoted(line, position + 1, field)
              : readPlain(line, position, field);
      if (position == MALFORMED) {
        return Optional.empty();
      }
      fields.add(field.toString());
      field.setLength(0);
      if (position >= line.length()) {
        return Optional.of(fields.toArray(String[]::new));
      }
      position++; // separator
    }
    return Optional.empty();
  }

  /** Reads up to the next separator; returns its position (or the line length). */
  private static int readPlain(String line, int start, StringBuilder field) {
    int separator = line.indexOf(SEPARATOR, start);
    int end = separator < 0 ? line.length() : separator;
    field.append(line, start, end);
    return end;
  }

  /** Reads a quoted value; returns the position of the following separator (or line end). */
  private static int readQuoted(String line, int start, StringBuilder field) {
    int position = start;
    while (position < line.length()) {
      char character = line.charAt(position);
      if (character != QUOTE) {
        field.append(character);
        position++;
      } else if (position + 1 < line.length() && line.charAt(position + 1) == QUOTE) {
        field.append(QUOTE);
        position += 2;
      } else {
        int next = position + 1;
        return next == line.length() || line.charAt(next) == SEPARATOR ? next : MALFORMED;
      }
    }
    return MALFORMED;
  }
}
