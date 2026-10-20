package dev.investai.market.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Filter over the stored indicators of every Paris instrument (spec 2.4, criteria 6 and 9). An
 * instrument whose filtered indicator is not available never matches.
 *
 * @param peaEligibility PEA eligibility required (owner's correction, or the rule), {@code null}
 *     for any
 * @param maxVolatility highest one-year volatility, in percent, {@code null} for any
 * @param minAverageDailyTradedAmount lowest average daily traded amount in euros; instruments
 *     traded in another currency never match. {@code null} for any
 * @param performanceWindow window of the performance filter and sort
 * @param minPerformance lowest performance over {@code performanceWindow}, in percent, {@code null}
 *     for any
 * @param sort order of the results
 * @param limit maximum number of results, 1 to {@link #MAX_LIMIT}
 */
public record ScreeningCriteria(
    @Nullable PeaEligibilityStatus peaEligibility,
    @Nullable BigDecimal maxVolatility,
    @Nullable BigDecimal minAverageDailyTradedAmount,
    IndicatorWindow performanceWindow,
    @Nullable BigDecimal minPerformance,
    ScreeningSort sort,
    int limit) {

  /** Maximum number of results of a screening. */
  public static final int MAX_LIMIT = 50;

  /** Checks the limit. */
  public ScreeningCriteria {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("Limit out of range");
    }
  }
}
