package dev.investai.market.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The latest daily computation of the indicators.
 *
 * @param asOf last session of its windows
 * @param computedAt when it ran
 */
public record IndicatorRun(LocalDate asOf, Instant computedAt) {}
