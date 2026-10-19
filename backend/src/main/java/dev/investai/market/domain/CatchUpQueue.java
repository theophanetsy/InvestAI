package dev.investai.market.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Port: closes still to fetch from EODHD after missed sessions, one entry per instrument. It lets a
 * catch-up interrupted by the daily call budget resume where it stopped.
 */
public interface CatchUpQueue {

  /**
   * Adds a period to catch up for some instruments; an existing entry is widened to cover both.
   *
   * @param instruments the instruments
   * @param from first missed session
   * @param to last missed session
   * @param requestedAt time of the request
   */
  void enqueue(
      Collection<InstrumentId> instruments, LocalDate from, LocalDate to, Instant requestedAt);

  /**
   * Lists the pending entries, oldest first.
   *
   * @return the entries
   */
  List<CatchUpRequest> pending();

  /**
   * Removes an entry once caught up, only if it was not widened meanwhile (the collection job may
   * run at the same time): a widened entry stays for the next run.
   *
   * @param request the entry as it was read
   */
  void remove(CatchUpRequest request);

  /**
   * A period of closes to fetch for one instrument.
   *
   * @param instrumentId the instrument
   * @param from first session, inclusive
   * @param to last session, inclusive
   */
  record CatchUpRequest(InstrumentId instrumentId, LocalDate from, LocalDate to) {}
}
