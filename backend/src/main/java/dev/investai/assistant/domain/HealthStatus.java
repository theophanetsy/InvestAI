package dev.investai.assistant.domain;

/** Whether the data the advice relies on is there (MCP health tool, phase 3.0). */
public enum HealthStatus {
  /** Closes are stored and the indicators were computed at least once. */
  READY,
  /** No close is stored yet: nothing to advise on. */
  NO_CLOSES,
  /** Closes are stored but the daily computation of the indicators never ran. */
  INDICATORS_NOT_COMPUTED
}
