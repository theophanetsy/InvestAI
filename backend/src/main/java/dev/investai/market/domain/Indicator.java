package dev.investai.market.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * A computed figure, or the reason why it is missing: never an approximate value (spec 2.4,
 * criterion 1).
 *
 * @param status available, or why not
 * @param value the value, present exactly when {@code status} is {@link IndicatorStatus#AVAILABLE}
 */
public record Indicator(IndicatorStatus status, @Nullable BigDecimal value) {

  /** Fewer than 80 % of the sessions of the window have a close. */
  public static final Indicator INSUFFICIENT_HISTORY =
      new Indicator(IndicatorStatus.INSUFFICIENT_HISTORY, null);

  /** Enough history, but the figure is undefined. */
  public static final Indicator NOT_COMPUTABLE =
      new Indicator(IndicatorStatus.NOT_COMPUTABLE, null);

  /** Checks that the value is given exactly when available. */
  public Indicator {
    boolean available = status == IndicatorStatus.AVAILABLE;
    boolean valued = value != null;
    if (available != valued) {
      throw new IllegalArgumentException("A value is given exactly when available");
    }
  }

  /**
   * An available indicator.
   *
   * @param value its value
   * @return the indicator
   */
  public static Indicator of(BigDecimal value) {
    return new Indicator(IndicatorStatus.AVAILABLE, value);
  }

  /**
   * Tells whether the value is given.
   *
   * @return {@code true} when available
   */
  public boolean available() {
    return status == IndicatorStatus.AVAILABLE;
  }
}
