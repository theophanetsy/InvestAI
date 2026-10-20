package dev.investai.assistant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.InstrumentId;
import dev.investai.portfolio.domain.CorrelationMatrix;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CorrelatedPairsTest {

  private static final InstrumentId A =
      new InstrumentId(UUID.fromString("00000000-0000-4000-8000-00000000000a"));
  private static final InstrumentId B =
      new InstrumentId(UUID.fromString("00000000-0000-4000-8000-00000000000b"));
  private static final InstrumentId C =
      new InstrumentId(UUID.fromString("00000000-0000-4000-8000-00000000000c"));

  private static final String ONE = "1.00";
  private static final String HIGH = "0.90";

  /** A-B 0.35, A-C 0.90, B-C not computable; the diagonal (1.00) is never a pair. */
  private final CorrelationMatrix matrix =
      new CorrelationMatrix(
          List.of(A, B, C),
          List.of(
              List.of(of(ONE), of("0.35"), of(HIGH)),
              List.of(of("0.35"), of(ONE), Indicator.NOT_COMPUTABLE),
              List.of(of(HIGH), Indicator.NOT_COMPUTABLE, of(ONE))));

  @Test
  void pairsOfDistinctLinesMostCorrelatedFirstWithoutUnavailableOnes() {
    assertThat(CorrelatedPairs.mostCorrelated(matrix, 20))
        .containsExactly(
            new CorrelatedPair(A, C, new BigDecimal(HIGH)),
            new CorrelatedPair(A, B, new BigDecimal("0.35")));
  }

  @Test
  void limitedToTheMaximum() {
    assertThat(CorrelatedPairs.mostCorrelated(matrix, 1))
        .containsExactly(new CorrelatedPair(A, C, new BigDecimal(HIGH)));
  }

  @Test
  void noPairWithoutTwoLines() {
    assertThat(
            CorrelatedPairs.mostCorrelated(
                new CorrelationMatrix(List.of(A), List.of(List.of(of(ONE)))), 20))
        .isEmpty();
  }

  private static Indicator of(String value) {
    return Indicator.of(new BigDecimal(value));
  }
}
