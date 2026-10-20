package dev.investai.assistant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HealthReportTest {

  private static final String VERSION = "1.2.3";
  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final IndicatorsFreshness COMPUTED =
      new IndicatorsFreshness(FRIDAY, Instant.parse("2026-10-19T08:00:00Z"));

  @Test
  void readyWhenClosesAndIndicatorsExist() {
    assertThat(new HealthReport(VERSION, FRIDAY, COMPUTED).status()).isEqualTo(HealthStatus.READY);
  }

  @Test
  void noClosesWinsOverEverythingElse() {
    assertThat(new HealthReport(VERSION, null, null).status()).isEqualTo(HealthStatus.NO_CLOSES);
    // Indicators computed, then every close deleted: still nothing to advise on.
    assertThat(new HealthReport(VERSION, null, COMPUTED).status())
        .isEqualTo(HealthStatus.NO_CLOSES);
  }

  @Test
  void closesWithoutComputationAreReported() {
    assertThat(new HealthReport(VERSION, FRIDAY, null).status())
        .isEqualTo(HealthStatus.INDICATORS_NOT_COMPUTED);
  }
}
