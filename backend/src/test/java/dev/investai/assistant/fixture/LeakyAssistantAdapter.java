package dev.investai.assistant.fixture;

import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.infrastructure.MarketConfiguration;
import dev.investai.portfolio.api.ProfileController;

/**
 * Deliberately reads a repository, an adapter and a controller of other modules, used only to prove
 * that {@code ArchitectureTest.ASSISTANT_USES_OTHER_MODULES_THROUGH_THEIR_APPLICATION} fails. Not a
 * Spring bean.
 */
public class LeakyAssistantAdapter {

  private final DailyPriceRepository prices;
  private final MarketConfiguration configuration;
  private final ProfileController controller;

  public LeakyAssistantAdapter(
      DailyPriceRepository prices,
      MarketConfiguration configuration,
      ProfileController controller) {
    this.prices = prices;
    this.configuration = configuration;
    this.controller = controller;
  }

  public DailyPriceRepository prices() {
    return prices;
  }

  public MarketConfiguration configuration() {
    return configuration;
  }

  public ProfileController controller() {
    return controller;
  }
}
