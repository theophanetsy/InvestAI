package dev.investai.market.domain;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;

/**
 * Indicators of one instrument on raw closes (spec 2.4): performance excluding dividends, and
 * neither the performance nor the risk is adjusted for splits or bonus shares (a split shows as a
 * drop of the price).
 *
 * @param asOf last session of every window
 * @param closesOneYear closes present among the 253 sessions of the one-year window (252 returns)
 * @param performance change of the close over each window, in percent at scale 2
 * @param volatility annualised standard deviation of the daily returns over one year, in percent at
 *     scale 2
 * @param maxDrawdown largest fall from a high to a later low over one year, in percent at scale 2,
 *     zero or negative
 * @param msciWorld compared with the MSCI World ETF (CW8)
 * @param cac40 compared with the CAC 40 ETF (CAC)
 * @param averageDailyTradedAmount mean of close × volume over three months (63 sessions), Euronext
 *     volumes only, in the currency of the instrument at scale 2
 */
public record IndicatorSet(
    LocalDate asOf,
    int closesOneYear,
    Map<IndicatorWindow, Indicator> performance,
    Indicator volatility,
    Indicator maxDrawdown,
    BenchmarkComparison msciWorld,
    BenchmarkComparison cac40,
    Indicator averageDailyTradedAmount) {

  /** Checks that every window has a performance. */
  public IndicatorSet {
    performance = Map.copyOf(performance);
    if (!performance.keySet().equals(EnumSet.allOf(IndicatorWindow.class))) {
      throw new IllegalArgumentException("One performance per window");
    }
  }

  /**
   * Returns the performance over a window.
   *
   * @param window the window
   * @return the change of the close, in percent
   */
  public Indicator performance(IndicatorWindow window) {
    Indicator indicator = performance.get(window);
    if (indicator == null) {
      throw new IllegalStateException("Checked by the constructor");
    }
    return indicator;
  }
}
