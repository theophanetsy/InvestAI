package dev.investai.market.domain;

import java.util.OptionalDouble;

/**
 * Daily returns on consecutive sessions, {@code NaN} where a close is missing (spec 2.4): a return
 * is only measured between two consecutive sessions of the calendar that both have a close. A
 * missing session removes the two returns around it; it is never interpolated nor merged into a
 * two-session return. Statistics are {@code double}: they are rounded before being exposed.
 */
public final class DailyReturns {

  private static final int MIN_OBSERVATIONS = 2;

  /** Below this variance per observation, a series is considered constant (rounding noise). */
  private static final double ZERO_VARIANCE = 1e-20;

  private final double[] values;

  /**
   * Creates the returns.
   *
   * @param values one return per session, {@code NaN} when unknown
   */
  public DailyReturns(double... values) {
    this.values = values.clone();
  }

  /**
   * Number of known returns.
   *
   * @return the returns that are not {@code NaN}
   */
  public int count() {
    int count = 0;
    for (double value : values) {
      if (!Double.isNaN(value)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Sample standard deviation (n - 1) of the known returns.
   *
   * @return the standard deviation, empty with fewer than two returns
   */
  public OptionalDouble standardDeviation() {
    return new Pairs(this, this).standardDeviation();
  }

  /**
   * Pearson correlation with other returns, on the sessions where both are known.
   *
   * @param other returns on the same sessions
   * @return between -1 and 1; empty with fewer than two common returns or a constant series
   */
  public OptionalDouble correlation(DailyReturns other) {
    Pairs pairs = new Pairs(this, other);
    if (pairs.count < MIN_OBSERVATIONS || pairs.constant()) {
      return OptionalDouble.empty();
    }
    double correlation = pairs.sxy / Math.sqrt(pairs.sxx * pairs.syy);
    return OptionalDouble.of(Math.clamp(correlation, -1.0, 1.0));
  }

  /**
   * Beta against a benchmark: covariance divided by the variance of the benchmark, on the sessions
   * where both are known.
   *
   * @param benchmark returns of the benchmark on the same sessions
   * @return the beta; empty with fewer than two common returns or a constant benchmark
   */
  public OptionalDouble beta(DailyReturns benchmark) {
    Pairs pairs = new Pairs(this, benchmark);
    if (pairs.count < MIN_OBSERVATIONS || pairs.syy / pairs.count < ZERO_VARIANCE) {
      return OptionalDouble.empty();
    }
    return OptionalDouble.of(pairs.sxy / pairs.syy);
  }

  /** Centred sums of squares and products over the sessions where both series are known. */
  private static final class Pairs {
    private int count;
    private double sxx;
    private double syy;
    private double sxy;

    Pairs(DailyReturns first, DailyReturns second) {
      if (first.values.length != second.values.length) {
        throw new IllegalArgumentException("Returns of different sessions");
      }
      double sumX = 0;
      double sumY = 0;
      for (int i = 0; i < first.values.length; i++) {
        if (known(first, second, i)) {
          count++;
          sumX += first.values[i];
          sumY += second.values[i];
        }
      }
      double meanX = count == 0 ? 0 : sumX / count;
      double meanY = count == 0 ? 0 : sumY / count;
      for (int i = 0; i < first.values.length; i++) {
        if (known(first, second, i)) {
          double dx = first.values[i] - meanX;
          double dy = second.values[i] - meanY;
          sxx += dx * dx;
          syy += dy * dy;
          sxy += dx * dy;
        }
      }
    }

    private static boolean known(DailyReturns first, DailyReturns second, int index) {
      return !Double.isNaN(first.values[index]) && !Double.isNaN(second.values[index]);
    }

    boolean constant() {
      return sxx / count < ZERO_VARIANCE || syy / count < ZERO_VARIANCE;
    }

    OptionalDouble standardDeviation() {
      return count < MIN_OBSERVATIONS
          ? OptionalDouble.empty()
          : OptionalDouble.of(Math.sqrt(sxx / (count - 1)));
    }
  }
}
