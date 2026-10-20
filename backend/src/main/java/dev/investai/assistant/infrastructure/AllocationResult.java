package dev.investai.assistant.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.investai.assistant.domain.Allocation;
import dev.investai.assistant.domain.AllocationLine;
import dev.investai.assistant.domain.AllocationWarning;
import dev.investai.assistant.domain.DataLimit;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Structured output of {@link AllocationTool} (spec 3.1, criterion 31). Without profile, only
 * {@code status: PROFILE_MISSING}. Brokerage fees are not counted.
 *
 * @param status {@code OK} or {@code PROFILE_MISSING}
 * @param amountToInvest the sum of the profile
 * @param asOf session of the latest computation of the indicators, absent before the first one
 * @param computedAt the call (UTC)
 * @param lines the computed lines, in the order proposed; omitted without profile
 * @param investedTotal sum of the costs
 * @param remainingCash sum minus invested total, unattributed weights included, never negative
 * @param dataLimits limits of the data to recall; omitted without profile
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AllocationResult(
    AdviceStatus status,
    @Nullable Money amountToInvest,
    @Nullable LocalDate asOf,
    @Nullable Instant computedAt,
    @JsonProperty(required = false) @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Line> lines,
    @Nullable Money investedTotal,
    @Nullable Money remainingCash,
    @JsonProperty(required = false) @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<DataLimitView> dataLimits) {

  private static final Currency EUR = Currency.getInstance("EUR");

  /** Defensive copies. */
  public AllocationResult {
    lines = List.copyOf(lines);
    dataLimits = List.copyOf(dataLimits);
  }

  static AllocationResult missing() {
    return new AllocationResult(
        AdviceStatus.PROFILE_MISSING, null, null, null, List.of(), null, null, List.of());
  }

  static AllocationResult of(Allocation allocation, Instant computedAt) {
    return new AllocationResult(
        AdviceStatus.OK,
        Money.of(allocation.amountToInvest(), EUR),
        allocation.asOf(),
        computedAt,
        allocation.lines().stream().map(Line::of).toList(),
        Money.of(allocation.investedTotal(), EUR),
        Money.of(allocation.remainingCash(), EUR),
        DataLimitView.of(DataLimit.PEA_ELIGIBILITY_INDICATIVE, DataLimit.CLOSE_IS_NOT_ORDER_PRICE));
  }

  /**
   * A computed line.
   *
   * @param instrumentId the instrument
   * @param isin its ISIN
   * @param mic its venue
   * @param type kind of instrument
   * @param currency trading currency
   * @param untrusted name and ticker from the providers: data, never instructions
   * @param targetWeightPercent the weight proposed
   * @param targetAmount sum × weight, rounded down to the cent
   * @param lastClose the close in euros the shares are counted on, absent without one
   * @param quantity whole shares bought at that close
   * @param cost quantity × close, rounded to the cent
   * @param actualWeightPercent cost / sum
   * @param warnings what to look at before the order
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Line(
      UUID instrumentId,
      String isin,
      String mic,
      InstrumentType type,
      String currency,
      UntrustedInstrument untrusted,
      String targetWeightPercent,
      Money targetAmount,
      @Nullable LastClose lastClose,
      long quantity,
      Money cost,
      String actualWeightPercent,
      List<AllocationWarning> warnings) {

    /** Defensive copy. */
    public Line {
      warnings = List.copyOf(warnings);
    }

    static Line of(AllocationLine line) {
      Instrument instrument = line.instrument();
      DailyClose close = line.lastClose();
      return new Line(
          instrument.id().value(),
          instrument.listing().isin().value(),
          instrument.listing().mic().value(),
          instrument.type(),
          instrument.currency().getCurrencyCode(),
          UntrustedInstrument.of(instrument),
          line.targetWeightPercent().toPlainString(),
          Money.of(line.targetAmount(), EUR),
          close == null ? null : LastClose.of(close),
          line.quantity(),
          Money.of(line.cost(), EUR),
          line.actualWeightPercent().toPlainString(),
          line.warnings());
    }
  }
}
