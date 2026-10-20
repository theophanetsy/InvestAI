package dev.investai.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import dev.investai.architecture.fixture.application.LeakyMcpUseCase;
import dev.investai.architecture.fixture.application.LeakyUseCase;
import dev.investai.assistant.fixture.LeakyAssistantAdapter;
import dev.investai.portfolio.fixture.LeakyPortfolioAdapter;
import org.junit.jupiter.api.Test;

/**
 * Proves that the rules actually detect violations: with no entity in the codebase yet, the
 * persistence rules would otherwise pass vacuously.
 */
class ArchitectureRulesTest {

  @Test
  void rejectsJpaTypesOutsideInfrastructure() {
    assertViolated(ArchitectureTest.ENTITIES_LIVE_IN_INFRASTRUCTURE, "MisplacedEmbeddable");
  }

  @Test
  void rejectsRepositoriesOutsideInfrastructure() {
    assertViolated(ArchitectureTest.REPOSITORIES_LIVE_IN_INFRASTRUCTURE, "MisplacedRepository");
  }

  @Test
  void rejectsJpaUsageInApplicationLayer() {
    assertViolated(ArchitectureTest.ONLY_INFRASTRUCTURE_USES_JPA, "EntityManager");
  }

  @Test
  void rejectsPortfolioCodeReachingIntoMarketAdaptersOrControllers() {
    var leaky = new ClassFileImporter().importClasses(LeakyPortfolioAdapter.class);

    assertThatThrownBy(
            () -> ArchitectureTest.PORTFOLIO_USES_MARKET_THROUGH_ITS_APPLICATION.check(leaky))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("MarketConfiguration")
        .hasMessageContaining("MarketController");
  }

  @Test
  void rejectsMcpLibraryOutsideInfrastructure() {
    var leaky = new ClassFileImporter().importClasses(LeakyMcpUseCase.class);

    assertThatThrownBy(() -> ArchitectureTest.ONLY_INFRASTRUCTURE_USES_MCP.check(leaky))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("McpSchema");
  }

  @Test
  void rejectsAssistantCodeReachingIntoRepositoriesAdaptersOrControllers() {
    var leaky = new ClassFileImporter().importClasses(LeakyAssistantAdapter.class);

    assertThatThrownBy(
            () ->
                ArchitectureTest.ASSISTANT_USES_OTHER_MODULES_THROUGH_THEIR_APPLICATION.check(
                    leaky))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("DailyPriceRepository")
        .hasMessageContaining("MarketConfiguration")
        .hasMessageContaining("ProfileController");
  }

  private static void assertViolated(ArchRule rule, String expectedViolation) {
    var violations =
        new ClassFileImporter()
            .importClasses(
                LeakyUseCase.class,
                LeakyUseCase.MisplacedEmbeddable.class,
                LeakyUseCase.MisplacedRepository.class);

    assertThatThrownBy(() -> rule.check(violations))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(expectedViolation);
  }
}
