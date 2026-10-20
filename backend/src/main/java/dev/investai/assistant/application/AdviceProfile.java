package dev.investai.assistant.application;

import dev.investai.assistant.domain.ProfileSummary;
import dev.investai.market.domain.EuronextCalendar;
import dev.investai.portfolio.application.InvestorProfiles;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The investor profile for the advice (MCP tool {@code getInvestorProfile}, spec 3.1, criteria 13
 * and 14). Reads it through the portfolio use case, never writes it.
 */
@Service
public class AdviceProfile {

  private final InvestorProfiles profiles;
  private final Clock clock;

  /**
   * Creates the use case.
   *
   * @param profiles the investor profile
   * @param clock current time
   */
  public AdviceProfile(InvestorProfiles profiles, Clock clock) {
    this.profiles = profiles;
    this.clock = clock;
  }

  /**
   * Summarises the current profile; the age of the PEA is reckoned in Paris.
   *
   * @return the summary, empty until the owner fills the profile in
   */
  public Optional<ProfileSummary> current() {
    LocalDate today = LocalDate.now(clock.withZone(EuronextCalendar.ZONE));
    return profiles.find().map(profile -> ProfileSummary.of(profile, today));
  }
}
