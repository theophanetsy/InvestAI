package dev.investai.assistant.infrastructure;

/** Whether the daily computation has produced indicators yet (spec 3.1, criterion 5). */
public enum IndicatorsStatus {
  /** Computed: {@code asOf}, {@code computedAt} and the indicators are given. */
  COMPUTED,
  /** Not computed yet (first start, or instrument not seen by the computation): no date. */
  NOT_COMPUTED
}
