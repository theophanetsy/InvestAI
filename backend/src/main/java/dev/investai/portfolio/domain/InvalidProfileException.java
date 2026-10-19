package dev.investai.portfolio.domain;

/**
 * A field of the profile breaks a rule that needs the current date; the API turns it into a {@code
 * validation.failed} on that field.
 */
public final class InvalidProfileException extends RuntimeException {

  /** Field: opening date of the PEA. */
  public static final String PEA_OPENED_ON = "peaOpenedOn";

  /** Constraint: not in the future. */
  public static final String PAST_OR_PRESENT = "PastOrPresent";

  private static final long serialVersionUID = 1L;

  private final String field;
  private final String constraint;

  /**
   * Creates the exception.
   *
   * @param field name of the faulty field, as sent by the client
   * @param constraint name of the violated constraint
   */
  public InvalidProfileException(String field, String constraint) {
    super("Invalid profile: " + field + " " + constraint);
    this.field = field;
    this.constraint = constraint;
  }

  /**
   * Returns the faulty field.
   *
   * @return e.g. {@link #PEA_OPENED_ON}
   */
  public String field() {
    return field;
  }

  /**
   * Returns the violated constraint.
   *
   * @return e.g. {@link #PAST_OR_PRESENT}
   */
  public String constraint() {
    return constraint;
  }
}
