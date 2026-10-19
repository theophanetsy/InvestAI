package dev.investai.market.domain;

import java.util.List;

/**
 * Reference data of a venue as returned by a provider.
 *
 * @param instruments the usable entries
 * @param ignored number of entries skipped (no ISIN, invalid ISIN or currency...)
 */
public record ReferenceData(List<ListedInstrument> instruments, int ignored) {

  /** Defensive copy. */
  public ReferenceData {
    instruments = List.copyOf(instruments);
  }
}
