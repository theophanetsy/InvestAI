package dev.investai.shared.domain;

/**
 * Formats of free text that the advice agent reads (spec 2.3, criterion 10; spec 3.1, criterion 7):
 * one definition of what is invisible, shared by the input checks of the REST API and of the MCP
 * tools, and by the cleaning of untrusted text sent to the model.
 */
public final class TextFormats {

  /**
   * Body of a character class matching what the owner would not see but a model would read: control
   * (Cc, C1 included), format (Cf: bidi, zero width, tags), private use (Co), unassigned (Cn), line
   * and paragraph separators (Zl, Zp).
   */
  public static final String INVISIBLE = "\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}\\p{Zl}\\p{Zp}";

  /**
   * Text on one line, without anything {@link #INVISIBLE}. A single character class: no recursion
   * in the regex engine, whatever the length.
   */
  public static final String SINGLE_LINE = "^[^" + INVISIBLE + "]*$";

  /** Like {@link #SINGLE_LINE}, plus tabs and line breaks. */
  public static final String MULTILINE = "^[\\t\\n\\r[^" + INVISIBLE + "]]*$";

  private TextFormats() {}
}
