package dev.investai.market.domain;

/**
 * PEA eligibility of an instrument and who decided it (spec 2.3, criterion 7).
 *
 * @param status eligible, not eligible or unknown
 * @param source the rule, or the owner's correction
 */
public record PeaEligibility(PeaEligibilityStatus status, PeaEligibilitySource source) {

  /**
   * An eligibility computed by the rule.
   *
   * @param status the result of the rule
   * @return the eligibility
   */
  public static PeaEligibility byRule(PeaEligibilityStatus status) {
    return new PeaEligibility(status, PeaEligibilitySource.RULE);
  }

  /**
   * An eligibility corrected by the owner.
   *
   * @param status the owner's answer
   * @return the eligibility
   */
  public static PeaEligibility byOwner(PeaEligibilityStatus status) {
    return new PeaEligibility(status, PeaEligibilitySource.OWNER);
  }
}
