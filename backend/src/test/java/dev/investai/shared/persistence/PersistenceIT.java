package dev.investai.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

@IntegrationTest
class PersistenceIT {

  private static final int POSTGRES_18 = 180_000;
  private static final int POSTGRES_19 = 190_000;

  @Autowired private Flyway flyway;
  @Autowired private JdbcClient jdbc;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private ApplicationContext context;

  @Test
  void flywayAppliesEveryMigrationAtStartup() {
    var info = flyway.info();

    assertThat(info.pending()).isEmpty();
    assertThat(info.applied()).extracting(MigrationInfo::getScript).contains("V1__baseline.sql");
  }

  @Test
  void databaseIsPostgresql18() {
    var version =
        jdbc.sql("select current_setting('server_version_num')::int").query(Integer.class).single();

    assertThat(version).isBetween(POSTGRES_18, POSTGRES_19 - 1);
  }

  @Test
  void hibernateOnlyValidatesTheSchemaOwnedByFlyway() {
    assertThat(entityManagerFactory.getProperties())
        .containsEntry("hibernate.hbm2ddl.auto", "validate");
  }

  @Test
  void openSessionInViewIsDisabled() {
    assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
  }
}
