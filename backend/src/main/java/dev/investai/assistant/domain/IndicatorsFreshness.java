package dev.investai.assistant.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The latest computation of the indicators, as reported to the assistant.
 *
 * @param asOf last session of its windows
 * @param computedAt when it ran
 */
public record IndicatorsFreshness(LocalDate asOf, Instant computedAt) {}
