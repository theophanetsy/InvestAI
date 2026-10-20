package dev.investai.market.api;

import dev.investai.market.domain.IndicatorWindow;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.PeaEligibilityStatus;
import dev.investai.market.domain.ScreeningCriteria;
import dev.investai.market.domain.ScreeningSort;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Query parameters of a screening. Decimals travel as strings checked by anchored patterns (never
 * parsed when too long); an instrument whose filtered indicator is not available never matches.
 *
 * @param peaEligibility PEA eligibility required; any when absent
 * @param maxVolatility highest one-year volatility, in percent
 * @param minAverageDailyTradedAmount lowest average daily traded amount in euros; instruments
 *     traded in another currency never match
 * @param performanceWindow window of the performance filter and sort, one year by default
 * @param minPerformance lowest performance over the window, in percent
 * @param sort best performance, lowest volatility, highest liquidity or smallest drawdown first
 * @param limit number of results, 1 to 50, 50 by default
 */
public record ScreeningRequest(
    @Nullable PeaEligibilityStatus peaEligibility,
    @Schema(example = "25") @Pattern(regexp = "^\\d{1,6}(\\.\\d{1,2})?$")
        @Nullable String maxVolatility,
    @Schema(example = "100000") @Pattern(regexp = "^\\d{1,13}(\\.\\d{1,2})?$")
        @Nullable String minAverageDailyTradedAmount,
    @Schema(defaultValue = "ONE_YEAR") @Nullable IndicatorWindow performanceWindow,
    @Schema(example = "0") @Pattern(regexp = "^-?\\d{1,6}(\\.\\d{1,2})?$")
        @Nullable String minPerformance,
    @Schema(defaultValue = "PERFORMANCE") @Nullable ScreeningSort sort,
    @Schema(defaultValue = "50") @Min(1) @Max(ScreeningCriteria.MAX_LIMIT)
        @Nullable Integer limit) {

  /**
   * Builds the criteria, with the defaults: every kind of instrument, no drawdown filter (the REST
   * screening of spec 2.4).
   *
   * @return the criteria
   */
  ScreeningCriteria toCriteria() {
    return new ScreeningCriteria(
        EnumSet.allOf(InstrumentType.class),
        peaEligibility == null ? EnumSet.allOf(PeaEligibilityStatus.class) : Set.of(peaEligibility),
        decimal(maxVolatility),
        null,
        decimal(minAverageDailyTradedAmount),
        Objects.requireNonNullElse(performanceWindow, IndicatorWindow.ONE_YEAR),
        decimal(minPerformance),
        Objects.requireNonNullElse(sort, ScreeningSort.PERFORMANCE),
        Objects.requireNonNullElse(limit, ScreeningCriteria.MAX_LIMIT));
  }

  /** Parses a parameter already checked by its pattern. */
  private static @Nullable BigDecimal decimal(@Nullable String text) {
    if (text == null) {
      return null;
    }
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Not a decimal number", ex);
    }
  }
}
