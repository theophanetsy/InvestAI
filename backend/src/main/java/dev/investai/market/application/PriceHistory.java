package dev.investai.market.application;

import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.Instrument;
import java.time.LocalDate;
import java.util.List;

/**
 * Raw closes of an instrument over its last sessions (spec 3.1, criterion 22).
 *
 * @param instrument the instrument
 * @param sessions the sessions of the period, in chronological order; empty without any close
 * @param closes the closes on those sessions, by ascending date (at most one per session)
 */
public record PriceHistory(
    Instrument instrument, List<LocalDate> sessions, List<DailyClose> closes) {

  /** Defensive copies. */
  public PriceHistory {
    sessions = List.copyOf(sessions);
    closes = List.copyOf(closes);
  }

  /**
   * Counts the sessions of the period without a close.
   *
   * @return the number of missing closes
   */
  public int missingSessions() {
    return sessions.size() - closes.size();
  }
}
