package dev.investai.portfolio.application;

import dev.investai.portfolio.domain.RiskProfile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The profile as typed by the owner, before it is dated and stored.
 *
 * @param amountToInvest single sum to invest at once, in euros
 * @param horizonYears investment horizon in years
 * @param riskProfile chosen risk profile
 * @param exclusions sectors or instruments to avoid
 * @param objectives free text (untrusted), {@code null} when not given
 * @param peaOpenedOn opening date of the PEA, {@code null} when not given
 */
public record ProfileChange(
    BigDecimal amountToInvest,
    int horizonYears,
    RiskProfile riskProfile,
    List<String> exclusions,
    @Nullable String objectives,
    @Nullable LocalDate peaOpenedOn) {

  /** Defensive copy. */
  public ProfileChange {
    exclusions = List.copyOf(exclusions);
  }
}
