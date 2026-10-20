package dev.investai.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.investai.portfolio.application.InvestorProfiles;
import dev.investai.portfolio.domain.InvestorProfile;
import dev.investai.portfolio.domain.RiskProfile;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdviceProfileTest {

  /** 22:30 UTC on October 19th is already October 20th in Paris (UTC+2). */
  private static final Instant LATE_EVENING_UTC = Instant.parse("2026-10-19T22:30:00Z");

  private final InvestorProfiles profiles = mock(InvestorProfiles.class);
  private final AdviceProfile advice =
      new AdviceProfile(profiles, Clock.fixed(LATE_EVENING_UTC, ZoneOffset.UTC));

  @Test
  void fiveYearsAreReckonedInParis() {
    var profile =
        new InvestorProfile(
            new BigDecimal("20000"),
            10,
            RiskProfile.PRUDENT,
            List.of(),
            null,
            LocalDate.parse("2021-10-20"),
            LATE_EVENING_UTC);
    when(profiles.find()).thenReturn(Optional.of(profile));

    assertThat(advice.current().orElseThrow().peaFiveYearsReached()).isTrue();
  }

  @Test
  void emptyWithoutProfile() {
    when(profiles.find()).thenReturn(Optional.empty());

    assertThat(advice.current()).isEmpty();
  }
}
