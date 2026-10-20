package dev.investai.portfolio.fixture;

import dev.investai.market.api.MarketController;
import dev.investai.market.infrastructure.MarketConfiguration;

/**
 * Deliberately reaches into the infrastructure and the controllers of the market module, used only
 * to prove that {@code ArchitectureTest.PORTFOLIO_USES_MARKET_THROUGH_ITS_APPLICATION} fails. Not a
 * Spring bean (test sources are not scanned for components outside the tests that need it).
 */
public class LeakyPortfolioAdapter {

  private final MarketConfiguration configuration;
  private final MarketController controller;

  public LeakyPortfolioAdapter(MarketConfiguration configuration, MarketController controller) {
    this.configuration = configuration;
    this.controller = controller;
  }

  public MarketConfiguration configuration() {
    return configuration;
  }

  public MarketController controller() {
    return controller;
  }
}
