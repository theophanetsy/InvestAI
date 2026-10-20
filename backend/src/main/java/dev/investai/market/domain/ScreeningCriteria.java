package dev.investai.market.domain;

import java.math.BigDecimal;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Filter over the stored indicators of every Paris instrument (spec 2.4, criteria 6 and 9; spec
 * 3.1, criterion 24). An instrument whose filtered indicator is not available never matches.
 *
 * @param types kinds of instrument kept, at least one
 * @param peaEligibilities PEA eligibilities kept (owner's correction, or the rule), at least one
 * @param maxVolatility highest one-year volatility, in percent, {@code null} for any
 * @param maxDrawdownLimit largest one-year fall allowed, in percent, positive or zero: keeps the
 *     instruments whose maximum drawdown is at least its opposite. {@code null} for any
 * @param minAverageDailyTradedAmount lowest average daily traded amount in euros; instruments
 *     traded in another currency never match. {@code null} for any
 * @param performanceWindow window of the performance filter and sort
 * @param minPerformance lowest performance over {@code performanceWindow}, in percent, {@code null}
 *     for any
 * @param sort order of the results
 * @param limit maximum number of results, 1 to {@link #MAX_LIMIT}
 */
public record ScreeningCriteria(
    Set<InstrumentType> types,
    Set<PeaEligibilityStatus> peaEligibilities,
    @Nullable BigDecimal maxVolatility,
    @Nullable BigDecimal maxDrawdownLimit,
    @Nullable BigDecimal minAverageDailyTradedAmount,
    IndicatorWindow performanceWindow,
    @Nullable BigDecimal minPerformance,
    ScreeningSort sort,
    int limit) {

  /** Maximum number of results of a screening. */
  public static final int MAX_LIMIT = 50;

  /** Checks the limit and the sets; defensive copies. */
  public ScreeningCriteria {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("Limit out of range");
    }
    if (types.isEmpty() || peaEligibilities.isEmpty()) {
      throw new IllegalArgumentException("At least one type and one eligibility");
    }
    if (maxDrawdownLimit != null && maxDrawdownLimit.signum() < 0) {
      throw new IllegalArgumentException("Negative drawdown limit");
    }
    types = Set.copyOf(types);
    peaEligibilities = Set.copyOf(peaEligibilities);
  }
}
