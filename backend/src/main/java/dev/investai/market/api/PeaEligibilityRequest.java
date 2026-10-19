package dev.investai.market.api;

import dev.investai.market.domain.PeaEligibilityStatus;
import jakarta.validation.constraints.NotNull;

/**
 * The owner's correction of the PEA eligibility of an instrument.
 *
 * @param status the owner's answer, which then prevails over the rule
 */
public record PeaEligibilityRequest(@NotNull PeaEligibilityStatus status) {}
