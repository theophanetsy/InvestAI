package dev.investai.market.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalisation used to search instruments by name, insensitive to case and accents. */
public final class SearchText {

  private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
  private static final Pattern SPACES = Pattern.compile("\\s+");

  private SearchText() {}

  /**
   * Normalises a text: accents removed, lower case, single spaces, trimmed.
   *
   * @param text a name or a query
   * @return the normalised text
   */
  public static String normalize(String text) {
    String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
    String plain = COMBINING_MARKS.matcher(decomposed).replaceAll("");
    return SPACES.matcher(plain.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
  }
}
