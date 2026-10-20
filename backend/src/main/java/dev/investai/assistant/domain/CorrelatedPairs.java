package dev.investai.assistant.domain;

import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.InstrumentId;
import dev.investai.portfolio.domain.CorrelationMatrix;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The most correlated pairs of lines of the portfolio, instead of the whole matrix (spec 3.1,
 * criterion 16): smaller, and what the advice needs (lines that move together).
 */
public final class CorrelatedPairs {

  private CorrelatedPairs() {}

  /**
   * Lists the pairs of distinct instruments whose correlation is available, most correlated first.
   *
   * @param matrix the correlations of the lines kept in the risk of the portfolio
   * @param max maximum number of pairs
   * @return the pairs, by decreasing correlation, then by identifiers
   */
  public static List<CorrelatedPair> mostCorrelated(CorrelationMatrix matrix, int max) {
    List<InstrumentId> instruments = matrix.instruments();
    List<CorrelatedPair> pairs = new ArrayList<>();
    for (int row = 0; row < instruments.size(); row++) {
      for (int column = row + 1; column < instruments.size(); column++) {
        Indicator correlation = matrix.rows().get(row).get(column);
        BigDecimal value = correlation.value();
        if (value != null) {
          pairs.add(new CorrelatedPair(instruments.get(row), instruments.get(column), value));
        }
      }
    }
    return pairs.stream()
        .sorted(
            Comparator.comparing(CorrelatedPair::correlation)
                .reversed()
                .thenComparing(pair -> pair.first().toString())
                .thenComparing(pair -> pair.second().toString()))
        .limit(max)
        .toList();
  }
}
