package dev.investai.market.domain;

/** Who decided a {@link PeaEligibility}. */
public enum PeaEligibilitySource {
  /** Computed by {@link PeaEligibilityRule}: an indication, to check with the broker. */
  RULE,
  /** Corrected by the owner: never overwritten by the rule nor by a reference data refresh. */
  OWNER
}
