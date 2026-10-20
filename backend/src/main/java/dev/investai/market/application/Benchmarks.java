package dev.investai.market.application;

import dev.investai.market.domain.IndicatorRun;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The configured benchmark ETFs present in the reference data.
 *
 * @param lastRun latest computation of the indicators, {@code null} when it never ran
 * @param benchmarks the benchmarks, in the order of {@code investai.market.benchmarks}
 */
public record Benchmarks(@Nullable IndicatorRun lastRun, List<Benchmark> benchmarks) {

  /** Defensive copy. */
  public Benchmarks {
    benchmarks = List.copyOf(benchmarks);
  }
}
