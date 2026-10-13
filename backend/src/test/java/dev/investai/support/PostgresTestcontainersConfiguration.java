package dev.investai.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Provides a throwaway PostgreSQL for integration tests. {@code @ServiceConnection} wires the
 * datasource to the container, so no JDBC URL or credentials are configured anywhere.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

  /** Must stay identical to the image in {@code infra/docker-compose.yml}. */
  public static final String POSTGRES_IMAGE = "postgres:18.6-alpine";

  /**
   * PostgreSQL container shared by every test class that reuses the same Spring context.
   *
   * @return the container, started by Spring
   */
  @Bean
  @ServiceConnection
  public PostgreSQLContainer postgres() {
    return new PostgreSQLContainer(POSTGRES_IMAGE);
  }
}
