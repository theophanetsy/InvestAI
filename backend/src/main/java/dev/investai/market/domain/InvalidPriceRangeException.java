package dev.investai.market.domain;

/** A requested price period is invalid; the API turns it into a {@code validation.failed}. */
public final class InvalidPriceRangeException extends RuntimeException {

  /** Constraint: the start must not be after the end. */
  public static final String NOT_AFTER_TO = "NotAfterTo";

  /** Constraint: at most five years. */
  public static final String MAX_FIVE_YEARS = "MaxFiveYears";

  private static final long serialVersionUID = 1L;

  private final String parameter;
  private final String constraint;

  /**
   * Creates the exception.
   *
   * @param parameter name of the faulty request parameter
   * @param constraint name of the violated constraint
   */
  public InvalidPriceRangeException(String parameter, String constraint) {
    super("Invalid price range: " + parameter + " " + constraint);
    this.parameter = parameter;
    this.constraint = constraint;
  }

  /**
   * Returns the faulty request parameter.
   *
   * @return e.g. {@code from}
   */
  public String parameter() {
    return parameter;
  }

  /**
   * Returns the violated constraint.
   *
   * @return e.g. {@link #NOT_AFTER_TO}
   */
  public String constraint() {
    return constraint;
  }
}
