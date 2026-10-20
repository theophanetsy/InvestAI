package dev.investai.assistant.domain;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * What the MCP health tool reports (phase 3.0): the application version and how recent the data is,
 * so that the model can tell the owner whether advice is possible and cite the dates.
 *
 * @param version version of the application
 * @param closesAsOf session of the latest stored close, {@code null} when none is stored
 * @param indicators latest computation of the indicators, {@code null} when it never ran
 */
public record HealthReport(
    String version, @Nullable LocalDate closesAsOf, @Nullable IndicatorsFreshness indicators) {

  /**
   * Summarises the report.
   *
   * @return {@link HealthStatus#NO_CLOSES} without any close, {@link
   *     HealthStatus#INDICATORS_NOT_COMPUTED} before the first computation, {@link
   *     HealthStatus#READY} otherwise
   */
  public HealthStatus status() {
    if (closesAsOf == null) {
      return HealthStatus.NO_CLOSES;
    }
    return indicators == null ? HealthStatus.INDICATORS_NOT_COMPUTED : HealthStatus.READY;
  }
}
