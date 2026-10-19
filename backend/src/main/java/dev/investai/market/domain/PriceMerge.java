package dev.investai.market.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Source priority applied before writing closes (ADR-0009): an {@link PriceSource#EURONEXT} close
 * is never replaced by an {@link PriceSource#EODHD} one; a difference between the two sources for
 * the same session is reported.
 *
 * @param toWrite closes to insert or update
 * @param conflicts sessions where the two sources disagree
 */
public record PriceMerge(List<DailyClose> toWrite, List<PriceKey> conflicts) {

  /** Defensive copies. */
  public PriceMerge {
    toWrite = List.copyOf(toWrite);
    conflicts = List.copyOf(conflicts);
  }

  /**
   * Merges incoming closes with the stored ones.
   *
   * @param incoming closes read from a provider
   * @param existing stored closes for the same instruments and sessions
   * @return what to write and what to report
   */
  public static PriceMerge of(Collection<DailyClose> incoming, Map<PriceKey, DailyClose> existing) {
    List<DailyClose> toWrite = new ArrayList<>();
    List<PriceKey> conflicts = new ArrayList<>();
    for (DailyClose close : incoming) {
      DailyClose stored = existing.get(close.key());
      if (stored != null
          && stored.source() != close.source()
          && stored.close().compareTo(close.close()) != 0) {
        conflicts.add(close.key());
      }
      boolean keepStored =
          stored != null
              && stored.source() == PriceSource.EURONEXT
              && close.source() == PriceSource.EODHD;
      if (!keepStored) {
        toWrite.add(close);
      }
    }
    return new PriceMerge(toWrite, conflicts);
  }
}
