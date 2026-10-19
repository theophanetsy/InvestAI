package dev.investai.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.domain.InstrumentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Valuation of the declared portfolio (spec 2.3, criterion 6). */
class ValuationTest {

  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");
  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");

  @Test
  void valueIsQuantityTimesTheLastCloseRoundedHalfEvenToTheCent() {
    // 3 x 10.0050 = 30.0150 -> 30.02 (half even: 1 is odd, rounds up); 1 x 0.0050 -> 0.00 (even)
    var valuation =
        Valuation.of(
            List.of(priced(line(3, null), "10.005"), priced(line(1, null), "0.005")), null);

    assertThat(valuation.lines())
        .extracting(ValuedLine::value)
        .containsExactly(new BigDecimal("30.02"), new BigDecimal("0.00"));
    assertThat(valuation.total()).isEqualTo(new BigDecimal("30.02"));
  }

  @Test
  void unrealizedGainNeedsTheAveragePrice() {
    var valuation =
        Valuation.of(
            List.of(
                priced(line(10, "150.1234"), "173.16"), // gain 230.366 -> 230.37, +15.35 %
                priced(line(4, "200"), "173.16"), // loss -107.36, -13.42 %
                priced(line(2, null), "173.16")),
            null);

    ValuedLine gain = valuation.lines().get(0);
    ValuedLine loss = valuation.lines().get(1);
    ValuedLine unknown = valuation.lines().get(2);
    assertThat(gain.unrealizedGain()).isEqualTo(new BigDecimal("230.37"));
    assertThat(gain.unrealizedGainPercent()).isEqualTo(new BigDecimal("15.35"));
    assertThat(loss.unrealizedGain()).isEqualTo(new BigDecimal("-107.36"));
    assertThat(loss.unrealizedGainPercent()).isEqualTo(new BigDecimal("-13.42"));
    assertThat(unknown.unrealizedGain()).isNull();
    assertThat(unknown.unrealizedGainPercent()).isNull();
  }

  @Test
  void weightsAreSharesOfTheTotalCashIncluded() {
    var valuation =
        Valuation.of(
            List.of(priced(line(1, null), "600"), priced(line(1, null), "200")),
            new Cash(new BigDecimal("200"), NOW));

    assertThat(valuation.total()).isEqualTo(new BigDecimal("1000.00"));
    assertThat(valuation.lines())
        .extracting(ValuedLine::weightPercent)
        .containsExactly(new BigDecimal("60.00"), new BigDecimal("20.00"));
    assertThat(valuation.cashWeightPercent()).isEqualTo(new BigDecimal("20.00"));
    assertThat(valuation.complete()).isTrue();
  }

  @Test
  void lineWithoutCloseIsFlaggedNeverValuedAtZero() {
    var valuation =
        Valuation.of(
            List.of(priced(line(5, "10"), "100"), new PricedLine(line(7, "20"), null)), null);

    ValuedLine unpriced = valuation.lines().get(1);
    assertThat(unpriced.priced()).isFalse();
    assertThat(unpriced.quote()).isNull();
    assertThat(unpriced.value()).isNull();
    assertThat(unpriced.weightPercent()).isNull();
    assertThat(unpriced.unrealizedGain()).isNull();
    assertThat(valuation.total()).isEqualTo(new BigDecimal("500.00"));
    assertThat(valuation.unpricedLines()).isEqualTo(1);
    assertThat(valuation.complete()).isFalse();
  }

  @Test
  void emptyPortfolioIsWorthZeroWithoutWeights() {
    var empty = Valuation.of(List.of(), null);
    var onlyUnpriced = Valuation.of(List.of(new PricedLine(line(1, null), null)), null);
    var zeroCash = Valuation.of(List.of(), new Cash(BigDecimal.ZERO, NOW));

    assertThat(empty.total()).isEqualTo(new BigDecimal("0.00"));
    assertThat(empty.lines()).isEmpty();
    assertThat(empty.cashWeightPercent()).isNull();
    assertThat(onlyUnpriced.lines().getFirst().weightPercent()).isNull();
    assertThat(zeroCash.total()).isEqualTo(new BigDecimal("0.00"));
    assertThat(zeroCash.cashWeightPercent()).isNull();
  }

  @Test
  void quoteKeepsTheDateOfTheClose() {
    var valuation = Valuation.of(List.of(priced(line(1, null), "1")), null);

    assertThat(valuation.lines().getFirst().quote()).isEqualTo(new Quote(BigDecimal.ONE, FRIDAY));
  }

  private static PortfolioLine line(long quantity, @Nullable String averagePrice) {
    return new PortfolioLine(
        PortfolioLineId.newId(),
        new InstrumentId(UUID.randomUUID()),
        quantity,
        averagePrice == null ? null : new BigDecimal(averagePrice),
        NOW);
  }

  private static PricedLine priced(PortfolioLine line, String close) {
    return new PricedLine(line, new Quote(new BigDecimal(close), FRIDAY));
  }
}
