package dev.investai.shared.logging;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Neutralises values before they reach a log message.
 *
 * <p>JSON logs escape control characters, but plain-text logs (profile {@code local}) do not: a
 * value containing line breaks or terminal escape sequences could forge log lines. Use it for
 * anything that may carry client input.
 */
public final class LogSanitizer {

  private static final Pattern CONTROL_CHARACTERS =
      Pattern.compile("[\\p{Cntrl}\\u0085\\u2028\\u2029]");

  private LogSanitizer() {}

  /**
   * Returns the value on a single line: control characters (CR, LF, ESC…) and Unicode line
   * separators are replaced by {@code _}.
   *
   * @param value any value, possibly {@code null}
   * @return the single-line representation
   */
  public static String oneLine(@Nullable Object value) {
    // The trailing replace() calls are redundant with the pattern, but they are what FindSecBugs
    // recognises as CR/LF neutralisation (CRLF_INJECTION_LOGS).
    return CONTROL_CHARACTERS
        .matcher(String.valueOf(value))
        .replaceAll("_")
        .replace("\r", "_")
        .replace("\n", "_");
  }
}
