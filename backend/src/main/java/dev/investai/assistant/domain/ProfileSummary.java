package dev.investai.assistant.domain;

import dev.investai.portfolio.domain.InvestorProfile;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * The investor profile as told to the model (MCP tool {@code getInvestorProfile}, spec 3.1,
 * criterion 14): the opening date of the PEA is replaced by whether its five years are reached
 * (least data, LLM02).
 *
 * @param profile the profile
 * @param peaFiveYearsReached whether the fifth anniversary of the PEA is reached, {@code null} when
 *     the opening date is not given
 */
public record ProfileSummary(InvestorProfile profile, @Nullable Boolean peaFiveYearsReached) {

  /** Age of a PEA after which a withdrawal no longer closes it nor is taxed as income. */
  public static final int PEA_MATURITY_YEARS = 5;

  /**
   * Summarises a profile.
   *
   * @param profile the profile
   * @param today the current date in Paris
   * @return the summary; five years are reached on the fifth anniversary of the opening
   */
  public static ProfileSummary of(InvestorProfile profile, LocalDate today) {
    LocalDate openedOn = profile.peaOpenedOn();
    return new ProfileSummary(
        profile, openedOn == null ? null : !openedOn.plusYears(PEA_MATURITY_YEARS).isAfter(today));
  }
}
