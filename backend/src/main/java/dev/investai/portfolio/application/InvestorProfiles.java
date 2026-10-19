package dev.investai.portfolio.application;

import dev.investai.portfolio.domain.InvestorProfile;
import dev.investai.portfolio.domain.InvestorProfileRepository;
import dev.investai.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and saves the investor profile (spec 2.3, criteria 1 and 2). Read later by the tools of the
 * advice agent (phase 3).
 */
@Service
public class InvestorProfiles {

  /** Error code of a profile not filled in yet. */
  public static final String NOT_FOUND = "profile.not-found";

  private static final Logger LOG = LoggerFactory.getLogger(InvestorProfiles.class);

  private final InvestorProfileRepository profiles;
  private final Clock clock;

  /**
   * Creates the use cases.
   *
   * @param profiles the stored profile
   * @param clock current time
   */
  public InvestorProfiles(InvestorProfileRepository profiles, Clock clock) {
    this.profiles = profiles;
    this.clock = clock;
  }

  /**
   * Returns the profile.
   *
   * @return the profile
   * @throws ResourceNotFoundException {@value #NOT_FOUND} until the owner fills it in
   */
  @Transactional(readOnly = true)
  public InvestorProfile get() {
    return profiles
        .find()
        .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND, "No investor profile yet"));
  }

  /**
   * Creates or replaces the profile (idempotent).
   *
   * @param change the profile typed by the owner
   * @return the stored profile
   */
  @Transactional
  public InvestorProfile save(ProfileChange change) {
    var profile =
        new InvestorProfile(
            change.amountToInvest(),
            change.horizonYears(),
            change.riskProfile(),
            change.exclusions(),
            change.objectives(),
            change.peaOpenedOn(),
            clock.instant());
    profiles.save(profile);
    // Identifiers only: no amount, no free text (ADR-0006).
    String risk = profile.riskProfile().name();
    LOG.info("Investor profile saved: riskProfile={}", risk);
    return profile;
  }
}
