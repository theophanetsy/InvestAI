package dev.investai.market.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * The 253 sessions of the Euronext calendar ending at the reference date of the indicators: 252
 * daily returns (one year), the first session being the reference of the one-year performance.
 * Holidays are not sessions; a session without close stays in the window (spec 2.4, criterion 1).
 *
 * @param sessions the sessions, in chronological order
 */
public record YearWindow(List<LocalDate> sessions) {

  /** Number of sessions: one year of returns plus the reference session. */
  public static final int SESSIONS = IndicatorWindow.ONE_YEAR.sessions() + 1;

  /** Checks the number of sessions. */
  public YearWindow {
    sessions = List.copyOf(sessions);
    if (sessions.size() != SESSIONS) {
      throw new IllegalArgumentException("A year window has " + SESSIONS + " sessions");
    }
  }

  /**
   * The window ending at a date.
   *
   * @param asOf last session (a non-trading day ends the window at the session before)
   * @param calendar trading days
   * @return the window
   */
  public static YearWindow ending(LocalDate asOf, EuronextCalendar calendar) {
    return new YearWindow(calendar.sessionsEnding(asOf, SESSIONS));
  }

  /**
   * Last session of the window.
   *
   * @return the reference date of the indicators
   */
  public LocalDate asOf() {
    return sessions.getLast();
  }

  /**
   * First session of the window.
   *
   * @return the reference session of the one-year performance
   */
  public LocalDate start() {
    return sessions.getFirst();
  }
}
