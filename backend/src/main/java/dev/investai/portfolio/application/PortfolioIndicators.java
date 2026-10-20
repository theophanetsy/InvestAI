package dev.investai.portfolio.application;

import dev.investai.market.domain.IndicatorSet;
import dev.investai.market.domain.InstrumentId;
import dev.investai.portfolio.domain.Composition;
import dev.investai.portfolio.domain.PortfolioRisk;
import java.time.LocalDate;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Indicators of the declared PEA, computed on demand from the stored closes (spec 2.4, criteria 7
 * and 8).
 *
 * @param overview the valuation and the held instruments
 * @param asOf last session of the closes used, {@code null} when no close is stored at all
 * @param lineIndicators indicators of each held instrument; empty without any stored close
 * @param composition shares and concentration
 * @param risk the current portfolio as if held for a year
 */
public record PortfolioIndicators(
    PortfolioOverview overview,
    @Nullable LocalDate asOf,
    Map<InstrumentId, IndicatorSet> lineIndicators,
    Composition composition,
    PortfolioRisk risk) {

  /** Defensive copy. */
  public PortfolioIndicators {
    lineIndicators = Map.copyOf(lineIndicators);
  }
}
