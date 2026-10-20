package dev.investai.market.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Port: the indicators of every instrument, computed daily (spec 2.4, criterion 6). */
public interface IndicatorRepository {

  /**
   * Inserts or replaces the indicators of instruments (idempotent), in one transaction.
   *
   * @param indicators the indicators, by instrument
   * @param computedAt time of the computation
   */
  void saveAll(Map<InstrumentId, IndicatorSet> indicators, Instant computedAt);

  /**
   * Returns the stored indicators of an instrument.
   *
   * @param id the instrument
   * @return the indicators, empty before the first computation that saw the instrument
   */
  Optional<StoredIndicators> find(InstrumentId id);

  /**
   * Returns the stored indicators of every instrument.
   *
   * @return the indicators, in no particular order
   */
  List<StoredIndicators> findAll();

  /**
   * Returns the latest computation.
   *
   * @return the computation, empty when the indicators were never computed
   */
  Optional<IndicatorRun> lastRun();
}
