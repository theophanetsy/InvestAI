package dev.investai.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.support.FixtureCredentials;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AuthPropertiesTest {

  private static final String USERNAME_PROPERTY = "investai.auth.username=";
  private static final String HASH_PROPERTY = "investai.auth.password-hash=";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(SingleUserConfiguration.class);

  @Test
  void bindsTheConfiguredUser() {
    var bound = new AtomicReference<@Nullable AuthProperties>();

    runner
        .withPropertyValues(
            FixtureCredentials.USERNAME_PROPERTY, FixtureCredentials.PASSWORD_HASH_PROPERTY)
        .run(context -> bound.set(context.getBean(AuthProperties.class)));

    assertThat(bound.get())
        .isEqualTo(
            new AuthProperties(FixtureCredentials.USERNAME, FixtureCredentials.PASSWORD_HASH));
  }

  @Test
  void refusesToStartWithoutUsername() {
    assertThat(startupFailure(USERNAME_PROPERTY, FixtureCredentials.PASSWORD_HASH_PROPERTY))
        .hasRootCauseInstanceOf(BindValidationException.class)
        .rootCause()
        .hasMessageContaining("username");
  }

  @Test
  void refusesToStartWithoutPasswordHash() {
    assertThat(startupFailure(FixtureCredentials.USERNAME_PROPERTY, HASH_PROPERTY))
        .hasRootCauseInstanceOf(BindValidationException.class)
        .rootCause()
        .hasMessageContaining("passwordHash");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{noop}plain-text-value",
        "{bcrypt}$2a$10$abcdefghijklmnopqrstuv",
        "{pbkdf2@SpringSecurity_v5_8}366991c8",
        "{pbkdf2@SpringSecurity_v5_8}366991C8B38D4965EF2170CFBBA7859DE431DFD2F540BC63E3C24DBB64BA4313FADBDB75C1637E780AD99CAB3B5584DF",
        "366991c8b38d4965ef2170cfbba7859de431dfd2f540bc63e3c24dbb64ba4313fadbdb75c1637e780ad99cab3b5584df"
      })
  void refusesToStartWithMalformedHash(String hash) {
    assertThat(startupFailure(FixtureCredentials.USERNAME_PROPERTY, HASH_PROPERTY + hash))
        .hasRootCauseMessage(SingleUserConfiguration.MALFORMED_HASH);
  }

  @Test
  void neverPrintsTheRejectedValue() {
    // Typical mistake: the clear password instead of its hash.
    String clearValue = FixtureCredentials.PASSPHRASE;

    var failure = startupFailure(FixtureCredentials.USERNAME_PROPERTY, HASH_PROPERTY + clearValue);

    assertThat(failure).hasRootCauseMessage(SingleUserConfiguration.MALFORMED_HASH);
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      assertThat(String.valueOf(cause.getMessage())).doesNotContain(clearValue);
    }
  }

  @Test
  void neverPrintsTheHash() {
    var properties =
        new AuthProperties(FixtureCredentials.USERNAME, FixtureCredentials.PASSWORD_HASH);

    assertThat(properties.toString()).doesNotContain(FixtureCredentials.PASSWORD_HASH);
  }

  private @Nullable Throwable startupFailure(String... properties) {
    var failure = new AtomicReference<@Nullable Throwable>();
    runner.withPropertyValues(properties).run(context -> failure.set(context.getStartupFailure()));
    return failure.get();
  }
}
