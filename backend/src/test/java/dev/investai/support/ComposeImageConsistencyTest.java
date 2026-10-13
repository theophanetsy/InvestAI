package dev.investai.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Tests must run against the same PostgreSQL version as the local environment. */
class ComposeImageConsistencyTest {

  private static final Path COMPOSE_FILE = Path.of("..", "infra", "docker-compose.yml");

  @Test
  void testcontainersImageMatchesDockerComposeImage() throws IOException {
    var compose = Files.readString(COMPOSE_FILE, StandardCharsets.UTF_8);

    assertThat(compose).contains("image: " + PostgresTestcontainersConfiguration.POSTGRES_IMAGE);
  }
}
