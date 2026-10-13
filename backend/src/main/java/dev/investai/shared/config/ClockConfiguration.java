package dev.investai.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exposes a UTC {@link Clock} so time-dependent code stays testable. */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

  /**
   * System clock in UTC.
   *
   * @return the application clock
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
