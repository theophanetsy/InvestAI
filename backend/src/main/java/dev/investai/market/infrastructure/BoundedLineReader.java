package dev.investai.market.infrastructure;

import java.io.IOException;
import java.io.Reader;
import org.jspecify.annotations.Nullable;

/**
 * Reads lines like {@link java.io.BufferedReader#readLine()}, but fails as soon as a line exceeds a
 * maximum length: a file without line breaks would otherwise end up as one huge string in memory.
 */
final class BoundedLineReader {

  private static final int BUFFER_SIZE = 8192;

  private final Reader in;
  private final int maxLength;
  private final char[] buffer = new char[BUFFER_SIZE];
  private int position;
  private int limit;

  BoundedLineReader(Reader in, int maxLength) {
    this.in = in;
    this.maxLength = maxLength;
  }

  /**
   * Reads the next line, without its terminator ({@code \n} or {@code \r\n}).
   *
   * @return the line, or {@code null} at the end of the input
   * @throws LineTooLongException when the line exceeds the maximum length
   * @throws IOException on a read failure
   */
  @Nullable String readLine() throws IOException {
    StringBuilder line = new StringBuilder();
    boolean read = false;
    while (fill()) {
      read = true;
      char character = buffer[position];
      position++;
      if (character == '\n') {
        return withoutCarriageReturn(line);
      }
      line.append(character);
      if (line.length() > maxLength + 1) {
        throw new LineTooLongException(maxLength);
      }
    }
    return read ? withoutCarriageReturn(line) : null;
  }

  private boolean fill() throws IOException {
    if (position < limit) {
      return true;
    }
    limit = Math.max(in.read(buffer), 0);
    position = 0;
    return limit > 0;
  }

  private String withoutCarriageReturn(StringBuilder line) throws LineTooLongException {
    int length = line.length();
    if (length > 0 && line.charAt(length - 1) == '\r') {
      line.setLength(length - 1);
    }
    if (line.length() > maxLength) {
      throw new LineTooLongException(maxLength);
    }
    return line.toString();
  }

  /** A line exceeds the maximum length. */
  static final class LineTooLongException extends IOException {

    private static final long serialVersionUID = 1L;

    LineTooLongException(int maxLength) {
      super("line longer than " + maxLength + " characters");
    }
  }
}
