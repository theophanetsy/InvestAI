package dev.investai.assistant.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.investai.assistant.domain.DataLimit;
import dev.investai.market.application.PriceHistory;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Structured output of {@link PriceHistoryTool} (spec 3.1, criterion 22): raw daily closes, never
 * aggregated, so that a one-day outlier stays visible.
 *
 * @param from first session of the period, absent without any close
 * @param to last session of the period: the latest close of the instrument
 * @param currency currency of the closes
 * @param sessionCount sessions of the period (Euronext calendar)
 * @param missingSessions sessions of the period without close
 * @param points the closes, by ascending date, at most 253
 * @param dataLimits limits of the data to recall
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PriceHistoryResult(
    @Nullable LocalDate from,
    @Nullable LocalDate to,
    String currency,
    int sessionCount,
    int missingSessions,
    List<Point> points,
    List<DataLimitView> dataLimits) {

  /** Defensive copies. */
  public PriceHistoryResult {
    points = List.copyOf(points);
    dataLimits = List.copyOf(dataLimits);
  }

  static PriceHistoryResult of(PriceHistory history) {
    List<LocalDate> sessions = history.sessions();
    return new PriceHistoryResult(
        sessions.isEmpty() ? null : sessions.getFirst(),
        sessions.isEmpty() ? null : sessions.getLast(),
        history.instrument().currency().getCurrencyCode(),
        sessions.size(),
        history.missingSessions(),
        history.closes().stream()
            .map(close -> new Point(close.tradingDate(), close.close().toPlainString()))
            .toList(),
        DataLimitView.of(DataLimit.RAW_CLOSES, DataLimit.OUTLIER_QUOTES));
  }

  /**
   * A raw close.
   *
   * @param date the session
   * @param close plain decimal string
   */
  public record Point(LocalDate date, String close) {}
}
