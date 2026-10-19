package dev.investai.market.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

/**
 * Port: sessions already collected ({@code market_collection}), one entry per Euronext file and one
 * per session caught up with EODHD.
 */
public interface CollectionJournal {

  /**
   * Tells whether a segment of a session is already collected (from its file or by catch-up).
   *
   * @param session the session
   * @param segment the file segment
   * @return {@code true} when nothing remains to download
   */
  boolean isCollected(LocalDate session, TradeFileSegment segment);

  /**
   * Records a processed Euronext file (idempotent).
   *
   * @param session the session of the file
   * @param segment the file segment
   * @param closes number of closes kept
   * @param processedAt processing time
   */
  void recordFile(LocalDate session, TradeFileSegment segment, int closes, Instant processedAt);

  /**
   * Records sessions caught up with EODHD (idempotent).
   *
   * @param sessions the missed sessions
   * @param processedAt processing time
   */
  void recordCatchUp(Collection<LocalDate> sessions, Instant processedAt);

  /**
   * Refreshes the number of closes of the caught-up sessions of a period.
   *
   * @param from first session, inclusive
   * @param to last session, inclusive
   */
  void refreshCatchUpCounts(LocalDate from, LocalDate to);

  /**
   * Returns the last collected session before a date.
   *
   * @param session exclusive upper bound
   * @return the session, empty when nothing was collected before
   */
  Optional<LocalDate> lastSessionBefore(LocalDate session);
}
