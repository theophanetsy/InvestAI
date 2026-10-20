package dev.investai.portfolio.domain;

import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.InstrumentId;
import java.util.List;

/**
 * Correlations of the daily returns over one year between the lines kept in the risk of the
 * portfolio (spec 2.4, criterion 7).
 *
 * @param instruments the instruments, in the order of the rows and columns
 * @param rows one row per instrument, one correlation per column
 */
public record CorrelationMatrix(List<InstrumentId> instruments, List<List<Indicator>> rows) {

  /** Defensive copy. */
  public CorrelationMatrix {
    instruments = List.copyOf(instruments);
    rows = List.copyOf(rows.stream().map(List::copyOf).toList());
  }
}
