package dev.investai.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import dev.investai.architecture.fixture.application.LeakyUseCase;
import org.junit.jupiter.api.Test;

/**
 * Proves that persistence rules actually detect violations: with no entity in the codebase yet,
 * they would otherwise pass vacuously.
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
