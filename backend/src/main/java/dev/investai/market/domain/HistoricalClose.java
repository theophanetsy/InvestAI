package dev.investai.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Raw close returned by a history provider, before it is attached to an instrument.
 *
 * @param date the session
 * @param close the raw ("as traded") close
 * @param volume units traded as published, {@code null} when not given
 */
public record HistoricalClose(LocalDate date, BigDecimal close, @Nullable BigDecimal volume) {

  /**
   * Creates a close without volume.
   *
   * @param date the session
   * @param close the raw close
   */
  public HistoricalClose(LocalDate date, BigDecimal close) {
    this(date, close, null);
  }
}
