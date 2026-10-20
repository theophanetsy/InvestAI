package dev.investai.portfolio.domain;

/** Why a line is left out of the risk of the portfolio (spec 2.4, criterion 8). */
public enum LineExclusion {
  /** No close in euros: the line has no value ("price unavailable"). */
  NOT_VALUED,
  /** Fewer than 80 % of the closes of the year: its past cannot be replayed. */
  INSUFFICIENT_HISTORY
}
