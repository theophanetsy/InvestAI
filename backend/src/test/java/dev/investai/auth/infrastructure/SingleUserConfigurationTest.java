package dev.investai.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.investai.support.FixtureCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class SingleUserConfigurationTest {

  private final PasswordEncoder encoder = new SingleUserConfiguration().passwordEncoder();

  @Test
  void acceptsHashProducedByTheScript() {
    assertThat(encoder.matches(FixtureCredentials.PASSPHRASE, FixtureCredentials.PASSWORD_HASH))
        .isTrue();
    assertThat(encoder.matches("Vecteur de test - pas un secret", FixtureCredentials.PASSWORD_HASH))
        .isFalse();
  }

  @Test
  void encodesWithTheScriptFormat() {
    assertThat(encoder.encode(FixtureCredentials.PASSPHRASE))
        .matches(SingleUserConfiguration.HASH_FORMAT);
  }

  @Test
  void refusesWeakerEncodings() {
    assertThatThrownBy(() -> encoder.matches("plain", "{noop}plain"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
