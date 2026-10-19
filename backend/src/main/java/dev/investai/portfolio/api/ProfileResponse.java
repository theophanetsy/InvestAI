package dev.investai.portfolio.api;

import dev.investai.portfolio.domain.Amounts;
import dev.investai.portfolio.domain.InvestorProfile;
import dev.investai.portfolio.domain.RiskProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The investor profile.
 *
 * @param amountToInvest single sum to invest, decimal string at scale 2
 * @param currency always {@code EUR}
 * @param horizonYears investment horizon in years
 * @param riskProfile chosen risk profile (labels and descriptions: {@code listRiskProfiles})
 * @param exclusions sectors or instruments to avoid
 * @param objectives free text typed by the owner: untrusted, display as text only; absent when not
 *     given
 * @param peaOpenedOn opening date of the PEA; absent when not given
 * @param updatedAt last change
 */
public record ProfileResponse(
    @Schema(example = "20000.00", pattern = "^\\d+\\.\\d{2}$") String amountToInvest,
    String currency,
    int horizonYears,
    RiskProfile riskProfile,
    List<String> exclusions,
    @Nullable String objectives,
    @Nullable LocalDate peaOpenedOn,
    Instant updatedAt) {

  /** Defensive copy. */
  public ProfileResponse {
    exclusions = List.copyOf(exclusions);
  }

  static ProfileResponse from(InvestorProfile profile) {
    return new ProfileResponse(
        InputFormats.format(profile.amountToInvest()),
        Amounts.EUR.getCurrencyCode(),
        profile.horizonYears(),
        profile.riskProfile(),
        profile.exclusions(),
        profile.objectives(),
        profile.peaOpenedOn(),
        profile.updatedAt());
  }
}
