package dev.investai.market.domain;

import java.util.List;

/**
 * Result of a screening.
 *
 * @param matchCount instruments matching the criteria, before the limit
 * @param results the first matching instruments, in the requested order
 */
public record Screening(int matchCount, List<InstrumentIndicators> results) {

  /** Defensive copy. */
  public Screening {
    results = List.copyOf(results);
  }
}
