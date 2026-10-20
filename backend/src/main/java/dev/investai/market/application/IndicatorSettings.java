package dev.investai.market.application;

import dev.investai.market.domain.Listing;

/**
 * Benchmarks of the indicators (spec 2.4, criterion 4), built from {@code
 * investai.market.indicators.*}. Both are among the tracked benchmarks: they have one year of
 * history.
 *
 * @param msciWorld the MSCI World ETF (Amundi, CW8)
 * @param cac40 the CAC 40 ETF (Amundi, CAC)
 */
public record IndicatorSettings(Listing msciWorld, Listing cac40) {}
