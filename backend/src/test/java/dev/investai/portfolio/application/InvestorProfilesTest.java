package dev.investai.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.investai.portfolio.domain.InvalidProfileException;
import dev.investai.portfolio.domain.InvestorProfileRepository;
import dev.investai.portfolio.domain.RiskProfile;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** The opening date of the PEA is checked with the application clock, in Paris time. */
class InvestorProfilesTest {

  /** 23:30 UTC on October 19th is already October 20th in Paris (UTC+2). */
  private static final Instant LATE_EVENING_UTC = Instant.parse("2026-10-19T23:30:00Z");

  private final InvestorProfileRepository repository = mock(InvestorProfileRepository.class);
  private final InvestorProfiles profiles =
      new InvestorProfiles(repository, Clock.fixed(LATE_EVENING_UTC, ZoneOffset.UTC));

  @Test
  void todayInParisIsAccepted() {
    var saved = profiles.save(change(LocalDate.parse("2026-10-20")));

    assertThat(saved.peaOpenedOn()).isEqualTo(LocalDate.parse("2026-10-20"));
    assertThat(saved.updatedAt()).isEqualTo(LATE_EVENING_UTC);
    Mockito.verify(repository).save(saved);
  }

  @Test
  void tomorrowInParisIsRefusedBeforeAnyWrite() {
    assertThatThrownBy(() -> profiles.save(change(LocalDate.parse("2026-10-21"))))
        .isInstanceOfSatisfying(
            InvalidProfileException.class,
            ex -> {
              assertThat(ex.field()).isEqualTo("peaOpenedOn");
              assertThat(ex.constraint()).isEqualTo("PastOrPresent");
            });
    Mockito.verifyNoInteractions(repository);
  }

  @Test
  void findReadsTheProfileWithoutFailingWhenThereIsNone() {
    var stored = profiles.save(change(LocalDate.parse("2026-10-20")));
    Mockito.when(repository.find()).thenReturn(Optional.of(stored));
    assertThat(profiles.find()).contains(stored);

    Mockito.when(repository.find()).thenReturn(Optional.empty());
    assertThat(profiles.find()).isEmpty();
  }

  private static ProfileChange change(LocalDate openedOn) {
    return new ProfileChange(
        new BigDecimal("1000"), 10, RiskProfile.PRUDENT, List.of(), null, openedOn);
  }
}
