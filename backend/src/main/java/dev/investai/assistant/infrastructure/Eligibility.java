package dev.investai.assistant.infrastructure;

import dev.investai.market.domain.PeaEligibility;
import dev.investai.market.domain.PeaEligibilitySource;
import dev.investai.market.domain.PeaEligibilityStatus;

/**
 * PEA eligibility of an instrument, as sent to the model: an indication to check with the broker.
 *
 * @param status {@code ELIGIBLE}, {@code NOT_ELIGIBLE} or {@code UNKNOWN}
 * @param source {@code RULE} (indicative rule) or {@code OWNER} (corrected by the owner)
 */
public record Eligibility(PeaEligibilityStatus status, PeaEligibilitySource source) {

  static Eligibility of(PeaEligibility eligibility) {
    return new Eligibility(eligibility.status(), eligibility.source());
  }
}
