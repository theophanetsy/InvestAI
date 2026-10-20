package dev.investai.assistant.domain;

import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.Instrument;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A computed line of an {@link Allocation}, amounts in euros at scale 2.
 *
 * @param instrument the instrument
 * @param targetWeightPercent the weight proposed, in percent
 * @param targetAmount sum times weight, rounded down to the cent
 * @param lastClose the close in euros the shares are counted on, {@code null} when there is none
 * @param quantity whole shares that the target amount buys at that close
 * @param cost quantity times close, rounded half-even to the cent, never above the target
 * @param actualWeightPercent cost / sum, in percent at scale 2 (half-even)
 * @param warnings what to look at, in the order of {@link AllocationWarning}
 */
public record AllocationLine(
    Instrument instrument,
    BigDecimal targetWeightPercent,
    BigDecimal targetAmount,
    @Nullable DailyClose lastClose,
    long quantity,
    BigDecimal cost,
    BigDecimal actualWeightPercent,
    List<AllocationWarning> warnings) {

  /** Defensive copy. */
  public AllocationLine {
    warnings = List.copyOf(warnings);
  }
}
