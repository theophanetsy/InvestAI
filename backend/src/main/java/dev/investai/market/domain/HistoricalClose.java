package dev.investai.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Raw close returned by a history provider, before it is attached to an instrument.
 *
 * @param date the session
 * @param close the raw ("as traded") close
 */
public record HistoricalClose(LocalDate date, BigDecimal close) {}
