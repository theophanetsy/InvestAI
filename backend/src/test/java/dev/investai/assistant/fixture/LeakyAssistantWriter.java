package dev.investai.assistant.fixture;

import dev.investai.market.application.InstrumentTracking;
import dev.investai.market.application.PeaEligibilityCorrection;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.PeaEligibilityStatus;
import dev.investai.portfolio.application.InvestorProfiles;
import dev.investai.portfolio.application.PortfolioCommands;
import dev.investai.portfolio.application.ProfileChange;
import java.math.BigDecimal;

/**
 * Deliberately calls write use cases, used only to prove that {@code
 * ArchitectureTest.ASSISTANT_NEVER_CALLS_A_WRITE_USE_CASE} fails. Not a Spring bean.
 */
public class LeakyAssistantWriter {

  private final InvestorProfiles profiles;
  private final PortfolioCommands commands;
  private final InstrumentTracking tracking;
  private final PeaEligibilityCorrection correction;

  public LeakyAssistantWriter(
      InvestorProfiles profiles,
      PortfolioCommands commands,
      InstrumentTracking tracking,
      PeaEligibilityCorrection correction) {
    this.profiles = profiles;
    this.commands = commands;
    this.tracking = tracking;
    this.correction = correction;
  }

  public void write(ProfileChange change, InstrumentId id) {
    profiles.save(change);
    commands.setCash(BigDecimal.ONE);
    tracking.track(id);
    correction.correct(id, PeaEligibilityStatus.ELIGIBLE);
  }
}
