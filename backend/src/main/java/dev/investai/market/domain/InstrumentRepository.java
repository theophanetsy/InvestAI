package dev.investai.market.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Port: the local reference data of instruments. */
public interface InstrumentRepository {

  /**
   * Finds an instrument.
   *
   * @param id its identifier
   * @return the instrument, if known
   */
  Optional<Instrument> findById(InstrumentId id);

  /**
   * Finds an instrument by listing.
   *
   * @param listing ISIN and venue
   * @return the instrument, if known
   */
  Optional<Instrument> findByListing(Listing listing);

  /**
   * Lists every instrument of the reference data, tracked or not, including those missing from the
   * latest refresh.
   *
   * @return all instruments, in a stable order
   */
  List<Instrument> findAll();

  /**
   * Lists the tracked instruments.
   *
   * @return tracked instruments, in a stable order
   */
  List<Instrument> findTracked();

  /**
   * Searches by exact ISIN, exact ticker (any case) or fragment of the name, ignoring case and
   * accents. Tracked instruments come first, then by name.
   *
   * @param query the text typed by the user
   * @param limit maximum number of results
   * @return matching instruments
   */
  List<Instrument> search(String query, int limit);

  /**
   * Inserts or updates reference entries, keyed by listing. Instruments missing from the entries
   * are kept untouched, with their history.
   *
   * @param instruments entries of the reference data
   * @param seenAt time of the refresh
   */
  void upsertReference(List<ListedInstrument> instruments, Instant seenAt);

  /**
   * Starts tracking an instrument; does nothing when it is already tracked.
   *
   * @param id the instrument
   * @param since tracking start
   * @return {@code true} when the instrument was not tracked before
   */
  boolean markTracked(InstrumentId id, Instant since);

  /**
   * Records that one year of closes has been loaded.
   *
   * @param id the instrument
   * @param at time of the load
   */
  void markHistoryLoaded(InstrumentId id, Instant at);

  /**
   * Returns the time of the last reference data refresh.
   *
   * @return the time, empty when the reference data has never been loaded
   */
  Optional<Instant> lastReferenceRefresh();
}
