package dev.investai.market.domain;

import java.time.Instant;

/**
 * Indicators of an instrument as stored by the daily computation (spec 2.4, criterion 6).
 *
 * @param instrumentId the instrument
 * @param indicators the indicators
 * @param computedAt when they were computed
 */
public record StoredIndicators(
    InstrumentId instrumentId, IndicatorSet indicators, Instant computedAt) {}
