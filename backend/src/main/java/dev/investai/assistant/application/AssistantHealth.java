package dev.investai.assistant.application;

import dev.investai.assistant.domain.HealthReport;
import dev.investai.assistant.domain.IndicatorsFreshness;
import dev.investai.market.application.MarketFreshness;
import dev.investai.market.domain.IndicatorRun;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Health of the data behind the advice (MCP tool {@code getHealth}, phase 3.0): version of the
 * application and dates of the latest closes and indicators. Reads the database through the market
 * use cases, never calls a provider.
 */
@Service
public class AssistantHealth {

  private final MarketFreshness market;
  private final String version;

  /**
   * Creates the use case.
   *
   * @param market freshness of the market data
   * @param version version of the application (Maven filtering)
   */
  public AssistantHealth(MarketFreshness market, @Value("${investai.version}") String version) {
    this.market = market;
    this.version = version;
  }

  /**
   * Builds the current health report.
   *
   * @return the report
   */
  public HealthReport report() {
    MarketFreshness.Freshness freshness = market.current();
    return new HealthReport(version, freshness.closesAsOf(), indicators(freshness.lastRun()));
  }

  private static @Nullable IndicatorsFreshness indicators(@Nullable IndicatorRun run) {
    return run == null ? null : new IndicatorsFreshness(run.asOf(), run.computedAt());
  }
}
