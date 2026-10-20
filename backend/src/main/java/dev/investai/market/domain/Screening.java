package dev.investai.market.domain;

import java.util.List;
import java.util.Map;

/**
 * Result of a screening.
 *
 * @param matchCount instruments matching the criteria, before the limit
 * @param results the first matching instruments, in the requested order
 * @param unavailable for each requested indicator filter only, the instruments of the requested
 *     types and eligibilities left out because that indicator is not available (an instrument
 *     lacking two filtered indicators counts twice)
 */
public record Screening(
    int matchCount, List<InstrumentIndicators> results, Map<ScreeningFilter, Integer> unavailable) {

  /** Defensive copies. */
  public Screening {
    results = List.copyOf(results);
    unavailable = Map.copyOf(unavailable);
  }

  /**
   * A screening without unavailability counts (REST screening, spec 2.4).
   *
   * @param matchCount instruments matching the criteria, before the limit
   * @param results the first matching instruments
   */
  public Screening(int matchCount, List<InstrumentIndicators> results) {
    this(matchCount, results, Map.of());
  }
}
