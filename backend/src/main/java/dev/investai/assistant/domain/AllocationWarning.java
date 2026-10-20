package dev.investai.assistant.domain;

/** What the owner must look at before following a computed line (spec 3.1, criterion 31). */
public enum AllocationWarning {
  /** The instrument is not eligible to the PEA (rule or owner's correction). */
  NOT_ELIGIBLE,
  /** Eligibility unknown (most ETFs until the owner corrects it): to check with the broker. */
  ELIGIBILITY_UNKNOWN,
  /** No close in euros: no share counted (never converted nor guessed). */
  NO_EUR_CLOSE,
  /** The latest close is older than the session of the indicators. */
  CLOSE_BEFORE_AS_OF,
  /** One share costs more than the target amount: no share counted. */
  TARGET_BELOW_ONE_SHARE
}
