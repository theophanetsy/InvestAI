package dev.investai.system.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StatusServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

  @Test
  void currentStatusReturnsApplicationNameAndClockTime() {
    var service = new StatusService(Clock.fixed(NOW, ZoneOffset.UTC), "investai-test");

    var status = service.currentStatus();

    assertThat(status).isEqualTo(new ApplicationStatus("investai-test", NOW));
  }
}
