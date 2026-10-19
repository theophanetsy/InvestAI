package dev.investai.market.application;

import dev.investai.market.domain.Listing;
import java.time.Duration;
import java.util.List;

/**
 * Settings of the market use cases, built from {@code investai.market.*}.
 *
 * @param dailyCallBudget EODHD calls allowed per UTC day (below the free quota of 20)
 * @param referenceMaxAge age after which the reference data is downloaded again
 * @param benchmarks listings tracked automatically once they exist in the reference data
 */
public record MarketSettings(
    int dailyCallBudget, Duration referenceMaxAge, List<Listing> benchmarks) {

  /** Defensive copy. */
  public MarketSettings {
    benchmarks = List.copyOf(benchmarks);
  }
}
