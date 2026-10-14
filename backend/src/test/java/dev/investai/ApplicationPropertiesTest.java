package dev.investai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/**
 * Guards the multi-document layout of {@code application.properties}: a {@code #---} separator
 * directly preceded by a comment is silently ignored, which would make the whole file conditional.
 */
class ApplicationPropertiesTest {

  private static final String ON_PROFILE = "spring.config.activate.on-profile";

  @Test
  void baseDocumentAppliesToEveryProfileAndJsonLogsExcludeLocal() throws IOException {
    var documents =
        new PropertiesPropertySourceLoader()
            .load("application", new ClassPathResource("application.properties"));

    assertThat(documents).hasSize(2);
    var base = documents.getFirst();
    assertThat(base.getProperty("spring.application.name")).hasToString("investai-backend");
    assertThat(base.getProperty("spring.jpa.hibernate.ddl-auto")).hasToString("validate");
    assertThat(base.containsProperty(ON_PROFILE)).isFalse();
    // Boot 4 ignores the former server.error.* keys: only spring.web.error.* is effective.
    assertThat(base.getProperty("spring.web.error.include-stacktrace")).hasToString("never");
    assertThat(base.getProperty("spring.web.error.include-message")).hasToString("never");
    assertThat(base.containsProperty("server.error.include-stacktrace")).isFalse();
    var structuredLogs = documents.get(1);
    assertThat(structuredLogs.getProperty(ON_PROFILE)).hasToString("!local");
    assertThat(structuredLogs.getProperty("logging.structured.format.console")).hasToString("ecs");
  }

  @Test
  void credentialsComeFromTheEnvironmentAndSessionCookieIsHardened() throws IOException {
    var base =
        new PropertiesPropertySourceLoader()
            .load("application", new ClassPathResource("application.properties"))
            .getFirst();

    assertThat(base.getProperty("investai.auth.username")).hasToString("${INVESTAI_USERNAME:}");
    assertThat(base.getProperty("investai.auth.password-hash"))
        .hasToString("${INVESTAI_PASSWORD_HASH:}");
    assertThat(base.getProperty("server.servlet.session.cookie.http-only")).hasToString("true");
    assertThat(base.getProperty("server.servlet.session.cookie.same-site")).hasToString("strict");
    assertThat(base.getProperty("server.servlet.session.cookie.secure")).hasToString("true");
  }

  @Test
  void localProfileListensOnLoopbackAndReadsInfraEnvFile() throws IOException {
    var local =
        new PropertiesPropertySourceLoader()
            .load("application-local", new ClassPathResource("application-local.properties"))
            .getFirst();

    assertThat(local.getProperty("spring.config.import"))
        .hasToString("optional:file:../infra/.env[.properties]");
    assertThat(local.getProperty("server.servlet.session.cookie.secure")).hasToString("false");
    assertThat(InetAddress.getByName(String.valueOf(local.getProperty("server.address"))))
        .matches(InetAddress::isLoopbackAddress, "loopback address");
  }
}
