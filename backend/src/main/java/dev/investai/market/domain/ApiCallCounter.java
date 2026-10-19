package dev.investai.market.domain;

import java.time.LocalDate;
import java.util.OptionalInt;

/** Port: calls made to a rate-limited provider, per day (UTC). */
@FunctionalInterface
public interface ApiCallCounter {

  /**
   * Reserves one call atomically, before it is made.
   *
   * @param day the day (UTC)
   * @param limit maximum number of calls for the day
   * @return the number of calls of the day including this one, or empty when the limit is reached
   */
  OptionalInt reserve(LocalDate day, int limit);
}
