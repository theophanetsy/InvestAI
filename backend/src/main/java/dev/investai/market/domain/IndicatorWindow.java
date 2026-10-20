package dev.investai.market.domain;

/**
 * Period of a performance indicator, in sessions of the Euronext calendar (spec 2.4, criterion 1).
 * A window of N sessions uses N + 1 closes: the reference session before the window, then its N
 * sessions.
 */
public enum IndicatorWindow {
  /** About one month. */
  ONE_MONTH(21),
  /** About three months. */
  THREE_MONTHS(63),
  /** About six months. */
  SIX_MONTHS(126),
  /** About one year. */
  ONE_YEAR(252);

  private final int sessions;

  IndicatorWindow(int sessions) {
    this.sessions = sessions;
  }

  /**
   * Number of sessions of the window.
   *
   * @return 21, 63, 126 or 252
   */
  public int sessions() {
    return sessions;
  }
}
