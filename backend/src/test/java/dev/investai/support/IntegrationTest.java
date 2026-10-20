package dev.investai.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Full application context backed by a real PostgreSQL (Testcontainers), served on a random port so
 * that tests can also go through the servlet container (cookies, {@code /error}, HTTP firewall).
 * Use it on every {@code *IT} class so they all share one cached context and one container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      FixtureCredentials.USERNAME_PROPERTY,
      FixtureCredentials.PASSWORD_HASH_PROPERTY,
      // /mcp mounted, as when the owner has configured the token (spec 3.0).
      FixtureCredentials.MCP_HASH_PROPERTY,
      // Scheduled jobs off and no EODHD key, even if the developer has one in the environment:
      // tests never call a provider, they trigger the use cases with fakes.
      "investai.market.jobs.enabled=false",
      "investai.market.eodhd.api-key="
    })
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
public @interface IntegrationTest {}
