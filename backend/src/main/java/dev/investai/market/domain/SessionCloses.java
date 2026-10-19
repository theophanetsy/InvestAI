package dev.investai.market.domain;

import java.util.List;
import java.util.Set;

/**
 * Outcome of a {@link SessionCloseCalculator}.
 *
 * @param closes one close per instrument of the reference data that traded on its venue
 * @param ambiguousTimestamps listings whose last trades share a timestamp with different prices
 *     (file order decided)
 * @param tradedElsewhereOnly listings with trades on another venue only: their venue rule is
 *     probably wrong
 * @param notStorable listings whose close has more than 4 decimal places (no close stored)
 * @param otherCurrency listings with order book trades in another currency than the instrument
 *     (ignored)
 * @param unknownIsins distinct ISINs of the session missing from the reference data (ignored)
 * @param unknownTrades trades of the session on those ISINs
 */
public record SessionCloses(
    List<DailyClose> closes,
    Set<Listing> ambiguousTimestamps,
    Set<Listing> tradedElsewhereOnly,
    Set<Listing> notStorable,
    Set<Listing> otherCurrency,
    int unknownIsins,
    long unknownTrades) {

  /** Defensive copies. */
  public SessionCloses {
    closes = List.copyOf(closes);
    ambiguousTimestamps = Set.copyOf(ambiguousTimestamps);
    tradedElsewhereOnly = Set.copyOf(tradedElsewhereOnly);
    notStorable = Set.copyOf(notStorable);
    otherCurrency = Set.copyOf(otherCurrency);
  }
}
