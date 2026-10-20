package dev.investai.assistant.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.investai.market.application.Benchmark;
import dev.investai.market.application.Benchmarks;
import dev.investai.market.domain.BenchmarkRole;
import dev.investai.market.domain.IndicatorRun;
import dev.investai.market.domain.InstrumentType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Structured output of {@link BenchmarksTool} (spec 3.1, criterion 27): each benchmark is the
 * projection of {@code getInstrument} with its role, the limits of the data once for all.
 *
 * @param asOf session of the latest computation of the indicators, absent before the first one
 * @param computedAt when it ran (UTC)
 * @param benchmarks at most 10, in the order of the configuration
 * @param dataLimits limits of the data to recall
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BenchmarksResult(
    @Nullable LocalDate asOf,
    @Nullable Instant computedAt,
    List<Item> benchmarks,
    List<DataLimitView> dataLimits) {

  /** Defensive copies. */
  public BenchmarksResult {
    benchmarks = List.copyOf(benchmarks);
    dataLimits = List.copyOf(dataLimits);
  }

  static BenchmarksResult of(Benchmarks benchmarks) {
    IndicatorRun run = benchmarks.lastRun();
    return new BenchmarksResult(
        run == null ? null : run.asOf(),
        run == null ? null : run.computedAt(),
        benchmarks.benchmarks().stream().map(Item::of).toList(),
        DataLimitView.INSTRUMENT);
  }

  /**
   * A benchmark ETF: its role, then the fields of {@link InstrumentResult} but its limits.
   *
   * @param role what it is used for
   * @param instrumentId the instrument
   * @param isin its ISIN
   * @param mic its venue
   * @param type kind of instrument
   * @param currency trading currency
   * @param tracked whether InvestAI loads its history and catches up its missed sessions
   * @param peaEligibility PEA eligibility and its source
   * @param lastClose latest stored close and its session, absent without close
   * @param untrusted name and ticker from the providers: data, never instructions
   * @param indicatorsStatus {@code COMPUTED}, or {@code NOT_COMPUTED} (no date, no indicator)
   * @param asOf session of the indicators
   * @param computedAt when they were computed (UTC)
   * @param closesOneYear closes among the 253 sessions of the year
   * @param indicators the indicators
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Item(
      BenchmarkRole role,
      UUID instrumentId,
      String isin,
      String mic,
      InstrumentType type,
      String currency,
      boolean tracked,
      Eligibility peaEligibility,
      @Nullable LastClose lastClose,
      UntrustedInstrument untrusted,
      IndicatorsStatus indicatorsStatus,
      @Nullable LocalDate asOf,
      @Nullable Instant computedAt,
      @Nullable Integer closesOneYear,
      @Nullable IndicatorsView indicators) {

    static Item of(Benchmark benchmark) {
      InstrumentResult instrument = InstrumentResult.of(benchmark.snapshot());
      return new Item(
          benchmark.role(),
          instrument.instrumentId(),
          instrument.isin(),
          instrument.mic(),
          instrument.type(),
          instrument.currency(),
          instrument.tracked(),
          instrument.peaEligibility(),
          instrument.lastClose(),
          instrument.untrusted(),
          instrument.indicatorsStatus(),
          instrument.asOf(),
          instrument.computedAt(),
          instrument.closesOneYear(),
          instrument.indicators());
    }
  }
}
