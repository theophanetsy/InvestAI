package dev.investai.auth.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/** The single InvestAI user (ADR-0007), checked against the PBKDF2 hash from the configuration. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class SingleUserConfiguration {

  /** Startup failure message; never includes the value, which may be a clear password. */
  static final String MALFORMED_HASH =
      "INVESTAI_PASSWORD_HASH is not a hash produced by scripts/hash-password.mjs";

  private static final String PASSWORD_ENCODING = "pbkdf2@SpringSecurity_v5_8";

  /**
   * Encoder id followed by hex(salt[16] + hash[32]). Checked here rather than by Bean Validation:
   * binding errors print the rejected value, which could be a clear password pasted by mistake.
   */
  static final Pattern HASH_FORMAT =
      Pattern.compile(Pattern.quote("{" + PASSWORD_ENCODING + "}") + "[0-9a-f]{96}");

  /**
   * Only the PBKDF2 format produced by {@code scripts/hash-password.mjs} is accepted.
   *
   * @return the encoder
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new DelegatingPasswordEncoder(
        PASSWORD_ENCODING,
        Map.of(PASSWORD_ENCODING, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
  }

  /**
   * Authenticates the single configured user. Unknown users and wrong passwords are
   * indistinguishable, timing included.
   *
   * @param properties the configured user
   * @param passwordEncoder the encoder
   * @return the authentication manager
   */
  @Bean
  AuthenticationManager authenticationManager(
      AuthProperties properties, PasswordEncoder passwordEncoder) {
    if (!HASH_FORMAT.matcher(properties.passwordHash()).matches()) {
      throw new IllegalStateException(MALFORMED_HASH);
    }
    UserDetails user =
        User.withUsername(properties.username())
            .password(properties.passwordHash())
            .authorities(List.of())
            .build();
    var provider = new DaoAuthenticationProvider(new InMemoryUserDetailsManager(user));
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }
}
