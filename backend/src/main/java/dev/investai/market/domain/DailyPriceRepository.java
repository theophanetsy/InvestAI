package dev.investai.market.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Port: stored daily closes, one per instrument and session. */
public interface DailyPriceRepository {

  /**
   * Loads the stored closes of some instruments over a period.
   *
   * @param instruments the instruments
   * @param from first session, inclusive
   * @param to last session, inclusive
   * @return the closes by key
   */
  Map<PriceKey, DailyClose> find(
      Collection<InstrumentId> instruments, LocalDate from, LocalDate to);

  /**
   * Inserts or replaces closes (idempotent). Callers apply {@link PriceMerge} first; the adapter
   * still never replaces an {@link PriceSource#EURONEXT} close by an {@link PriceSource#EODHD} one.
   *
   * @param closes the closes
   * @param collectedAt time of the collection
   */
  void save(Collection<DailyClose> closes, Instant collectedAt);

  /**
   * Lists the closes of an instrument over a period.
   *
   * @param instrument the instrument
   * @param from first session, inclusive
   * @param to last session, inclusive
   * @return closes by ascending date
   */
  List<DailyClose> findBetween(InstrumentId instrument, LocalDate from, LocalDate to);

  /**
   * Returns the most recent close of an instrument.
   *
   * @param instrument the instrument
   * @return the close, empty when none is stored
   */
  Optional<DailyClose> findLatest(InstrumentId instrument);

  /**
   * Returns the most recent close of each instrument.
   *
   * @param instruments the instruments
   * @return the closes by instrument; instruments without close are absent
   */
  Map<InstrumentId, DailyClose> findLatest(Collection<InstrumentId> instruments);

  /**
   * Counts the closes of a session coming from a source.
   *
   * @param session the session
   * @param source the source
   * @return the number of closes
   */
  int count(LocalDate session, PriceSource source);

  /**
   * Returns the latest session with a close strictly before a date.
   *
   * @param day the date, excluded
   * @return the session, empty when no close is stored before it
   */
  Optional<LocalDate> latestSessionBefore(LocalDate day);

  /**
   * Returns the time of the latest write of a close (collection, history or catch-up).
   *
   * @return the time, empty when no close is stored
   */
  Optional<Instant> lastWrite();
}
