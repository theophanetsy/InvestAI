package dev.investai.assistant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.portfolio.domain.InvestorProfile;
import dev.investai.portfolio.domain.RiskProfile;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ProfileSummaryTest {

  private static final LocalDate TODAY = LocalDate.parse("2026-10-20");

  @Test
  void fiveYearsAreReachedOnTheFifthAnniversary() {
    assertThat(
            ProfileSummary.of(profile(LocalDate.parse("2021-10-20")), TODAY).peaFiveYearsReached())
        .isTrue();
    assertThat(
            ProfileSummary.of(profile(LocalDate.parse("2021-10-21")), TODAY).peaFiveYearsReached())
        .isFalse();
  }

  @Test
  void unknownWithoutOpeningDate() {
    assertThat(ProfileSummary.of(profile(null), TODAY).peaFiveYearsReached()).isNull();
  }

  private static InvestorProfile profile(@Nullable LocalDate openedOn) {
    return new InvestorProfile(
        new BigDecimal("20000"),
        10,
        RiskProfile.BALANCED,
        List.of(),
        null,
        openedOn,
        Instant.parse("2026-10-19T08:00:00Z"));
  }
}
