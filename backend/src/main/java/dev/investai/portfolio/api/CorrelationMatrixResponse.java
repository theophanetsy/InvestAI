package dev.investai.portfolio.api;

import dev.investai.market.api.IndicatorResponse;
import dev.investai.market.domain.InstrumentId;
import dev.investai.portfolio.domain.CorrelationMatrix;
import java.util.List;
import java.util.UUID;

/**
 * Correlations of the daily returns over one year between the lines kept in the risk of the
 * portfolio (values between -1 and 1, scale 2).
 *
 * @param instrumentIds the instruments, in the order of the rows and of the columns
 * @param rows one row per instrument, one correlation per column
 */
public record CorrelationMatrixResponse(
    List<UUID> instrumentIds, List<List<IndicatorResponse>> rows) {

  /** Defensive copies. */
  public CorrelationMatrixResponse {
    instrumentIds = List.copyOf(instrumentIds);
    rows = List.copyOf(rows.stream().map(List::copyOf).toList());
  }

  static CorrelationMatrixResponse from(CorrelationMatrix matrix) {
    return new CorrelationMatrixResponse(
        matrix.instruments().stream().map(InstrumentId::value).toList(),
        matrix.rows().stream()
            .map(row -> row.stream().map(IndicatorResponse::from).toList())
            .toList());
  }
}
