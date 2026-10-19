package dev.investai.market.domain;

/** Whether an instrument can be held in a French PEA (plan d'épargne en actions). */
public enum PeaEligibilityStatus {
  /** Can be held in a PEA; "probable, check with the broker" when it comes from the rule. */
  ELIGIBLE,
  /** Cannot be held in a PEA. */
  NOT_ELIGIBLE,
  /** Not deducible from the reference data (funds and ETFs), until the owner says. */
  UNKNOWN
}
