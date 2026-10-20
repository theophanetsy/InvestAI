package dev.investai.assistant.domain;

/**
 * Cleans text that the model must read as data, never as instructions (spec 3.1, criterion 7):
 * instrument names from the providers, objectives and exclusions typed by the owner. The text is
 * then placed alone in a JSON field under {@code untrusted}, escaped by Jackson: no delimiter of
 * our own to neutralise.
 *
 * <ul>
 *   <li>invisible characters removed: control (Cc), format (Cf: zero width, bidi), private use
 *       (Co), unassigned (Cn), line and paragraph separators (Zl, Zp), and lone surrogates (Cs);
 *   <li>tabs and line breaks replaced by a space;
 *   <li>bounded in code points, a longer text ending with "…".
 * </ul>
 */
public final class UntrustedText {

  /** Tab, line feed, carriage return, next line (NEL), line and paragraph separators. */
  private static final String LINE_BREAKS_AND_TAB = "\t\n\r\u0085\u2028\u2029";

  private static final String ELLIPSIS = "…";
  private static final int MIN_BOUND = 2;

  private UntrustedText() {}

  /**
   * Cleans and bounds a text.
   *
   * @param text any text
   * @param maxLength maximum number of code points of the result, ellipsis included, at least 2
   * @return the cleaned text
   */
  public static String clean(String text, int maxLength) {
    if (maxLength < MIN_BOUND) {
      throw new IllegalArgumentException("Bound too small");
    }
    StringBuilder visible = new StringBuilder(text.length());
    text.codePoints()
        .forEach(
            codePoint -> {
              if (isLineBreakOrTab(codePoint)) {
                visible.append(' ');
              } else if (!isInvisible(codePoint)) {
                visible.appendCodePoint(codePoint);
              }
            });
    String cleaned = visible.toString();
    if (cleaned.codePointCount(0, cleaned.length()) <= maxLength) {
      return cleaned;
    }
    return cleaned.substring(0, cleaned.offsetByCodePoints(0, maxLength - 1)) + ELLIPSIS;
  }

  private static boolean isLineBreakOrTab(int codePoint) {
    return LINE_BREAKS_AND_TAB.indexOf(codePoint) >= 0;
  }

  private static boolean isInvisible(int codePoint) {
    return switch (Character.getType(codePoint)) {
      case Character.CONTROL,
          Character.FORMAT,
          Character.PRIVATE_USE,
          Character.UNASSIGNED,
          Character.LINE_SEPARATOR,
          Character.PARAGRAPH_SEPARATOR,
          Character.SURROGATE ->
          true;
      default -> false;
    };
  }
}
