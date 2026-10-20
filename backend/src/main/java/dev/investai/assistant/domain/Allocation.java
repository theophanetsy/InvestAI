package dev.investai.assistant.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a split of the sum to invest would buy (spec 3.1, criterion 30), amounts in euros at scale
 * 2; nothing is recorded.
 *
 * @param amountToInvest the sum of the investor profile
 * @param asOf session of the latest computation of the indicators, {@code null} when it never ran
 * @param lines the computed lines, in the order proposed
 * @param investedTotal sum of the costs
 * @param remainingCash sum minus invested total (unattributed weights included), never negative
 */
public record Allocation(
    BigDecimal amountToInvest,
    @Nullable LocalDate asOf,
    List<AllocationLine> lines,
    BigDecimal investedTotal,
    BigDecimal remainingCash) {

  /** Defensive copy. */
  public Allocation {
    lines = List.copyOf(lines);
  }
}
