package dev.investai.market.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Closes of some instruments and of the two benchmarks on a {@link YearWindow}: everything the
 * indicators of an instrument, or of a portfolio, are computed from.
 *
 * @param window the sessions
 * @param closes closes by instrument; an instrument without any close may be absent
 * @param msciWorld closes of the MSCI World ETF (CW8), empty when it is not in the reference data
 * @param cac40 closes of the CAC 40 ETF (CAC), empty when it is not in the reference data
 * @param euronextSessions sessions of the window whose two Euronext files were collected
 */
public record YearOfCloses(
    YearWindow window,
    Map<InstrumentId, ClosingPrices> closes,
    ClosingPrices msciWorld,
    ClosingPrices cac40,
    Set<LocalDate> euronextSessions) {

  /** Defensive copies. */
  public YearOfCloses {
    closes = Map.copyOf(closes);
    euronextSessions = Set.copyOf(euronextSessions);
  }

  /**
   * Closes of an instrument.
   *
   * @param id the instrument
   * @return its closes on the window, possibly none
   */
  public ClosingPrices of(InstrumentId id) {
    ClosingPrices prices = closes.get(id);
    return prices == null ? ClosingPrices.ofValues(window.sessions(), Map.of()) : prices;
  }

  /**
   * Computes the indicators of an instrument.
   *
   * @param id the instrument
   * @return its indicators as of the last session of the window
   */
  public IndicatorSet indicatorsOf(InstrumentId id) {
    return IndicatorCalculator.compute(of(id), msciWorld, cac40, euronextSessions);
  }
}
