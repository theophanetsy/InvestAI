package dev.investai.market.domain;

import java.time.LocalDate;

/**
 * Identifies a daily close.
 *
 * @param instrumentId the instrument
 * @param tradingDate the session
 */
public record PriceKey(InstrumentId instrumentId, LocalDate tradingDate) {}
