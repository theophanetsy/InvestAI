package dev.investai.architecture.fixture.application;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EntityManager;
import org.springframework.data.repository.Repository;

/**
 * Deliberately misplaced persistence code, used only to prove the ArchUnit rules can fail. Not a
 * Spring bean; the nested types are ignored by entity/repository scanning in integration tests.
 */
public class LeakyUseCase {

  private final EntityManager entityManager;

  public LeakyUseCase(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public EntityManager entityManager() {
    return entityManager;
  }

  /** Embeddable outside infrastructure. */
  @Embeddable
  public static class MisplacedEmbeddable {
    private String value = "";

    public String value() {
      return value;
    }

    public void value(String newValue) {
      value = newValue;
    }
  }

  /** Spring Data repository outside infrastructure. */
  public interface MisplacedRepository extends Repository<MisplacedEmbeddable, String> {}
}
