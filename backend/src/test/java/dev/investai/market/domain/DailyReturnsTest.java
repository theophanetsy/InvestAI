package dev.investai.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Statistics of daily returns (spec 2.4, criteria 3 and 4), on small series computed by hand. */
class DailyReturnsTest {

  private static final double NAN = Double.NaN;

  @Test
  void sampleStandardDeviationDividesByCountMinusOne() {
    // Mean 5, squared deviations 9+1+1+1+0+0+4+16 = 32, sample variance 32 / 7: √(32/7) = 2.13809.
    var returns = new DailyReturns(2, 4, 4, 4, 5, 5, 7, 9);

    assertThat(returns.standardDeviation().getAsDouble()).isCloseTo(2.13809, within(1e-5));
    assertThat(returns.count()).isEqualTo(8);
  }

  @Test
  void unknownReturnsAreLeftOutNeverReplaced() {
    // Same as above once the NaN are removed.
    var returns = new DailyReturns(2, NAN, 4, 4, 4, NAN, 5, 5, 7, 9);

    assertThat(returns.count()).isEqualTo(8);
    assertThat(returns.standardDeviation().getAsDouble()).isCloseTo(2.13809, within(1e-5));
  }

  @Test
  void standardDeviationNeedsTwoReturns() {
    assertThat(new DailyReturns(0.1, NAN).standardDeviation()).isEmpty();
    assertThat(new DailyReturns().standardDeviation()).isEmpty();
  }

  @Test
  void correlationAndBetaOfSmallSeries() {
    // x = 1,2,3,4 and y = 1,3,2,4: deviations ±1.5, ±0.5 → Σdxdy = 2.25 - 0.25 - 0.25 + 2.25 = 4,
    // Σdx² = Σdy² = 5 → correlation 4/5 = 0.8; beta of y against x = 4/5 = 0.8.
    var x = new DailyReturns(1, 2, 3, 4);
    var y = new DailyReturns(1, 3, 2, 4);

    assertThat(y.correlation(x).getAsDouble()).isCloseTo(0.8, within(1e-12));
    assertThat(y.beta(x).getAsDouble()).isCloseTo(0.8, within(1e-12));
  }

  @Test
  void proportionalReturnsHaveCorrelationOneAndTheirRatioAsBeta() {
    var benchmark = new DailyReturns(0.01, -0.02, 0.005, 0.0);
    var asset = new DailyReturns(0.02, -0.04, 0.01, 0.0);

    assertThat(asset.correlation(benchmark).getAsDouble()).isCloseTo(1.0, within(1e-12));
    assertThat(asset.beta(benchmark).getAsDouble()).isCloseTo(2.0, within(1e-12));
    assertThat(benchmark.beta(asset).getAsDouble()).isCloseTo(0.5, within(1e-12));
  }

  @Test
  void onlySessionsKnownInBothSeriesArePaired() {
    // Pairs kept: (1,1), (3,2), (4,4) once the sessions unknown on either side are dropped.
    // Means 8/3 and 7/3; deviations of x: -5/3, 1/3, 4/3; of y: -4/3, -1/3, 5/3.
    // Σdxdy = (20 - 1 + 20)/9 = 39/9, Σdx² = Σdy² = 42/9 → correlation 39/42 = 0.928571.
    var x = new DailyReturns(1, 2, 3, 4, NAN);
    var y = new DailyReturns(1, NAN, 2, 4, 5);

    assertThat(x.correlation(y).getAsDouble()).isCloseTo(39.0 / 42, within(1e-12));
  }

  @Test
  void aConstantSeriesHasNoCorrelationAndAZeroBeta() {
    var constant = new DailyReturns(0, 0, 0, 0);
    var moving = new DailyReturns(0.01, -0.01, 0.02, 0.0);

    assertThat(constant.correlation(moving)).isEmpty();
    assertThat(moving.correlation(constant)).isEmpty();
    assertThat(constant.beta(moving).getAsDouble()).isZero();
    assertThat(moving.beta(constant)).as("constant benchmark").isEmpty();
  }

  @Test
  void correlationNeedsTwoCommonReturns() {
    var x = new DailyReturns(0.01, NAN, 0.02);
    var y = new DailyReturns(NAN, 0.03, 0.02);

    assertThat(x.correlation(y)).isEmpty();
    assertThat(x.beta(y)).isEmpty();
  }

  @Test
  void returnsOfDifferentLengthsAreRejected() {
    var x = new DailyReturns(0.01, 0.02);
    var y = new DailyReturns(0.01);

    assertThatIllegalArgumentException().isThrownBy(() -> x.correlation(y));
  }
}
