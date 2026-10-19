package dev.investai.market.domain;

/**
 * Market Identifier Code (ISO 10383) of a trading venue, e.g. {@code XPAR} for the regulated market
 * of Euronext Paris or {@code ALXP} for Euronext Growth Paris.
 *
 * @param value four upper-case letters or digits
 */
public record Mic(String value) {

  /** Euronext Paris regulated market. */
  public static final Mic XPAR = new Mic("XPAR");

  /** Euronext Growth Paris. */
  public static final Mic ALXP = new Mic("ALXP");

  private static final int LENGTH = 4;
  private static final String ALLOWED = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

  /** Validates the value; the message never echoes it. */
  public Mic {
    if (!isWellFormed(value)) {
      throw new IllegalArgumentException("Invalid MIC");
    }
  }

  /** Checked by hand: a static pattern would not be initialised yet for the constants above. */
  private static boolean isWellFormed(String value) {
    if (value.length() != LENGTH) {
      return false;
    }
    return value.chars().allMatch(c -> ALLOWED.indexOf(c) >= 0);
  }

  @Override
  public String toString() {
    return value;
  }
}
