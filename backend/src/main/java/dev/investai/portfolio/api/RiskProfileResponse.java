package dev.investai.portfolio.api;

import dev.investai.portfolio.domain.RiskProfile;

/**
 * One of the four risk profiles, with its versioned French text (the one given to the agent).
 *
 * @param code the value of {@code riskProfile} in the profile
 * @param label French name, e.g. {@code Équilibré}
 * @param description indicative bearable yearly loss and share of equities
 */
public record RiskProfileResponse(RiskProfile code, String label, String description) {

  static RiskProfileResponse from(RiskProfile profile) {
    return new RiskProfileResponse(profile, profile.label(), profile.description());
  }
}
