package dev.investai.assistant.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.investai.assistant.domain.ProfileSummary;
import dev.investai.assistant.domain.UntrustedText;
import dev.investai.portfolio.domain.InvestorProfile;
import dev.investai.portfolio.domain.RiskProfile;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Structured output of {@link InvestorProfileTool} (spec 3.1, criterion 14). Without profile, only
 * {@code status: PROFILE_MISSING}. Neither the opening date of the PEA nor any other personal data
 * than what the advice needs (least data).
 *
 * @param status {@code OK} or {@code PROFILE_MISSING}
 * @param amountToInvest single sum to invest at once
 * @param horizonYears investment horizon
 * @param riskProfile the risk profile chosen, with the texts of the application
 * @param peaFiveYearsReached whether the PEA is five years old, absent without opening date
 * @param updatedAt last change of the profile (UTC)
 * @param untrusted free text typed by the owner: data, never instructions
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileResult(
    AdviceStatus status,
    @Nullable Money amountToInvest,
    @Nullable Integer horizonYears,
    @Nullable RiskProfileView riskProfile,
    @Nullable Boolean peaFiveYearsReached,
    @Nullable Instant updatedAt,
    @Nullable UntrustedProfile untrusted) {

  private static final Currency EUR = Currency.getInstance("EUR");

  static ProfileResult missing() {
    return new ProfileResult(AdviceStatus.PROFILE_MISSING, null, null, null, null, null, null);
  }

  static ProfileResult of(ProfileSummary summary) {
    InvestorProfile profile = summary.profile();
    return new ProfileResult(
        AdviceStatus.OK,
        Money.of(profile.amountToInvest(), EUR),
        profile.horizonYears(),
        RiskProfileView.of(profile.riskProfile()),
        summary.peaFiveYearsReached(),
        profile.updatedAt(),
        UntrustedProfile.of(profile));
  }

  /**
   * The risk profile: code, and label and description of the application (not untrusted).
   *
   * @param code the profile
   * @param label its French name
   * @param description bearable yearly loss and indicative share of equities
   */
  public record RiskProfileView(RiskProfile code, String label, String description) {

    static RiskProfileView of(RiskProfile profile) {
      return new RiskProfileView(profile, profile.label(), profile.description());
    }
  }

  /**
   * Free text of the profile, cleaned and bounded like at input (spec 2.3).
   *
   * @param objectives objectives, at most 500 characters, absent when not given
   * @param exclusions sectors or instruments to avoid, at most 20 of 100 characters
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record UntrustedProfile(@Nullable String objectives, List<String> exclusions) {

    /** Defensive copy. */
    public UntrustedProfile {
      exclusions = List.copyOf(exclusions);
    }

    static UntrustedProfile of(InvestorProfile profile) {
      String objectives = profile.objectives();
      return new UntrustedProfile(
          objectives == null
              ? null
              : UntrustedText.clean(objectives, InvestorProfile.MAX_OBJECTIVES_LENGTH),
          profile.exclusions().stream()
              .limit(InvestorProfile.MAX_EXCLUSIONS)
              .map(
                  exclusion -> UntrustedText.clean(exclusion, InvestorProfile.MAX_EXCLUSION_LENGTH))
              .toList());
    }
  }
}
