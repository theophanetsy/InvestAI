package dev.investai.market.application;

import dev.investai.market.domain.BenchmarkRole;

/**
 * A configured benchmark ETF (spec 3.1, criterion 27).
 *
 * @param role what it is used for
 * @param snapshot the instrument, its latest close and its indicators
 */
public record Benchmark(BenchmarkRole role, InstrumentSnapshot snapshot) {}
