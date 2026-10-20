package dev.investai.portfolio.application;

import dev.investai.portfolio.domain.InvalidProfileException;
import dev.investai.portfolio.domain.InvestorProfile;
import dev.investai.portfolio.domain.InvestorProfileRepository;
import dev.investai.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
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

  /** The owner's calendar: the PEA is opened on a date in France. */
  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

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
   * Reads the profile, when filled in (MCP tools of the advice, spec 3.1: a missing profile is a
   * state, not an error).
   *
   * @return the profile, empty until the owner fills it in
   */
  @Transactional(readOnly = true)
  public Optional<InvestorProfile> find() {
    return profiles.find();
  }

  /**
   * Creates or replaces the profile (idempotent).
   *
   * @param change the profile typed by the owner
   * @return the stored profile
   * @throws InvalidProfileException when the opening date of the PEA is in the future (Paris)
   */
  @Transactional
  public InvestorProfile save(ProfileChange change) {
    LocalDate openedOn = change.peaOpenedOn();
    if (openedOn != null && openedOn.isAfter(LocalDate.now(clock.withZone(PARIS)))) {
      throw new InvalidProfileException(
          InvalidProfileException.PEA_OPENED_ON, InvalidProfileException.PAST_OR_PRESENT);
    }
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
    // Nothing typed by the owner (ADR-0006): no amount, no risk profile, no free text.
    LOG.info("Investor profile saved");
    return profile;
  }
}
