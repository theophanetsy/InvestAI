package dev.investai.assistant.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.investai.assistant.domain.HealthReport;
import dev.investai.assistant.domain.HealthStatus;
import dev.investai.assistant.domain.IndicatorsFreshness;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Structured output of {@link HealthTool}: a projection dedicated to the model, not a REST DTO
 * (ADR-0010). Absent dates are left out of the JSON.
 *
 * @param status {@code READY}, {@code NO_CLOSES} or {@code INDICATORS_NOT_COMPUTED}
 * @param version version of the application
 * @param closesAsOf session of the latest stored close
 * @param indicatorsAsOf reference session of the latest computation of the indicators
 * @param indicatorsComputedAt when that computation ran (UTC)
 */
// Absent rather than null: the output schema types the dates as strings, and the MCP server
// validates the structured content against it.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthResult(
    HealthStatus status,
    String version,
    @Nullable LocalDate closesAsOf,
    @Nullable LocalDate indicatorsAsOf,
    @Nullable Instant indicatorsComputedAt) {

  static HealthResult from(HealthReport report) {
    IndicatorsFreshness indicators = report.indicators();
    return new HealthResult(
        report.status(),
        report.version(),
        report.closesAsOf(),
        indicators == null ? null : indicators.asOf(),
        indicators == null ? null : indicators.computedAt());
  }
}
