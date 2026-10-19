package dev.investai.portfolio.api;

import dev.investai.portfolio.application.ProfileChange;
import dev.investai.portfolio.domain.InvestorProfile;
import dev.investai.portfolio.domain.RiskProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The investor profile typed by the owner.
 *
 * @param amountToInvest single sum to invest at once, in euros: decimal string, positive, at most 2
 *     decimal places
 * @param horizonYears investment horizon, 1 to 40 years
 * @param riskProfile risk profile chosen among four
 * @param exclusions sectors or instruments to avoid, 20 at most, 100 characters each; empty (or
 *     absent) when none
 * @param objectives free text, 500 characters at most; untrusted data, stored as typed
 * @param peaOpenedOn opening date of the PEA, not in the future (Paris time, checked by the use
 *     case with the application clock)
 */
public record ProfileRequest(
    @NotNull
        @Pattern(regexp = InputFormats.POSITIVE_AMOUNT)
        @Schema(example = "20000.00", pattern = InputFormats.POSITIVE_AMOUNT)
        String amountToInvest,
    @Min(InvestorProfile.MIN_HORIZON_YEARS) @Max(InvestorProfile.MAX_HORIZON_YEARS)
        int horizonYears,
    @NotNull RiskProfile riskProfile,
    @Size(max = InvestorProfile.MAX_EXCLUSIONS)
        List<
                @NotBlank @Size(max = InvestorProfile.MAX_EXCLUSION_LENGTH)
                @Pattern(regexp = InputFormats.SINGLE_LINE) String>
            exclusions,
    @Size(max = InvestorProfile.MAX_OBJECTIVES_LENGTH) @Pattern(regexp = InputFormats.MULTILINE)
        @Nullable String objectives,
    @Nullable LocalDate peaOpenedOn) {

  /** Defensive copy; an absent list means no exclusion. */
  public ProfileRequest {
    exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
  }

  ProfileChange toChange() {
    return new ProfileChange(
        InputFormats.parse(amountToInvest),
        horizonYears,
        riskProfile,
        exclusions,
        objectives,
        peaOpenedOn);
  }
}
