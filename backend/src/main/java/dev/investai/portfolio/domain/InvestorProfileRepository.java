package dev.investai.portfolio.domain;

import java.util.Optional;

/** Port: the investor profile, a single one (single user). */
public interface InvestorProfileRepository {

  /**
   * Returns the profile.
   *
   * @return the profile, empty until the owner fills it in
   */
  Optional<InvestorProfile> find();

  /**
   * Creates or replaces the profile (idempotent).
   *
   * @param profile the profile
   */
  void save(InvestorProfile profile);
}
