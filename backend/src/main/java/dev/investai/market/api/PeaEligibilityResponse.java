package dev.investai.market.api;

import dev.investai.market.domain.PeaEligibility;
import dev.investai.market.domain.PeaEligibilitySource;
import dev.investai.market.domain.PeaEligibilityStatus;

/**
 * PEA eligibility of an instrument. From the rule, {@code ELIGIBLE} only means "probable, check
 * with the broker"; from the owner, it is the owner's answer.
 *
 * @param status eligible, not eligible or unknown
 * @param source {@code RULE} (EU/EEA shares, configured benchmarks) or {@code OWNER} (correction)
 */
public record PeaEligibilityResponse(PeaEligibilityStatus status, PeaEligibilitySource source) {

  /**
   * Converts the domain value; also used by the portfolio API, so that the generated client has a
   * single type for it.
   *
   * @param eligibility the eligibility
   * @return the response
   */
  public static PeaEligibilityResponse from(PeaEligibility eligibility) {
    return new PeaEligibilityResponse(eligibility.status(), eligibility.source());
  }
}
