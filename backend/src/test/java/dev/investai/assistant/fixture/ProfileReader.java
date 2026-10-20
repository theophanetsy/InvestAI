package dev.investai.assistant.fixture;

import dev.investai.portfolio.application.InvestorProfiles;
import dev.investai.portfolio.domain.InvestorProfile;
import java.util.Optional;

/**
 * Reads the profile like the advice does, used only to prove that {@code
 * ArchitectureTest.ASSISTANT_NEVER_CALLS_A_WRITE_USE_CASE} allows the read methods of a use case
 * that also writes. Not a Spring bean.
 */
public class ProfileReader {

  private final InvestorProfiles profiles;

  public ProfileReader(InvestorProfiles profiles) {
    this.profiles = profiles;
  }

  public Optional<InvestorProfile> read() {
    return profiles.find();
  }
}
