package dev.investai.market.domain;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Screens the stored indicators of every Paris instrument (spec 2.4, criteria 6 and 9): filters,
 * sorts and limits, without computing anything. Done in memory over ~1,300 rows: the PEA
 * eligibility is resolved in Java (owner's correction or rule), not stored.
 */
public final class IndicatorScreen {

  private static final Currency EUR = Currency.getInstance("EUR");

  private IndicatorScreen() {}

  /**
   * Applies criteria.
   *
   * @param candidates every instrument with stored indicators
   * @param criteria the filter, order and limit
   * @return the matching instruments
   */
  public static Screening apply(
      Collection<InstrumentIndicators> candidates, ScreeningCriteria criteria) {
    List<InstrumentIndicators> matching =
        candidates.stream()
            .filter(candidate -> matches(candidate, criteria))
            .sorted(order(criteria))
            .toList();
    return new Screening(
        matching.size(), matching.subList(0, Math.min(criteria.limit(), matching.size())));
  }

  private static boolean matches(InstrumentIndicators candidate, ScreeningCriteria criteria) {
    IndicatorSet indicators = candidate.indicators().indicators();
    PeaEligibilityStatus eligibility = criteria.peaEligibility();
    BigDecimal minAmount = criteria.minAverageDailyTradedAmount();
    boolean eligible =
        eligibility == null || candidate.instrument().peaEligibility().status() == eligibility;
    boolean liquid = minAmount == null || liquidInEuros(candidate, minAmount);
    return eligible
        && liquid
        && atMost(indicators.volatility(), criteria.maxVolatility())
        && atLeast(indicators.performance(criteria.performanceWindow()), criteria.minPerformance());
  }

  private static boolean liquidInEuros(InstrumentIndicators candidate, BigDecimal minAmount) {
    return EUR.equals(candidate.instrument().currency())
        && atLeast(candidate.indicators().indicators().averageDailyTradedAmount(), minAmount);
  }

  private static boolean atLeast(Indicator indicator, @Nullable BigDecimal bound) {
    if (bound == null) {
      return true;
    }
    BigDecimal value = indicator.value();
    return value != null && value.compareTo(bound) >= 0;
  }

  private static boolean atMost(Indicator indicator, @Nullable BigDecimal bound) {
    if (bound == null) {
      return true;
    }
    BigDecimal value = indicator.value();
    return value != null && value.compareTo(bound) <= 0;
  }

  private static Comparator<InstrumentIndicators> order(ScreeningCriteria criteria) {
    Comparator<InstrumentIndicators> byIndicator =
        switch (criteria.sort()) {
          case PERFORMANCE ->
              byValue(
                  candidate -> indicators(candidate).performance(criteria.performanceWindow()),
                  true);
          case VOLATILITY -> byValue(candidate -> indicators(candidate).volatility(), false);
          case LIQUIDITY -> byValue(IndicatorScreen::amountInEuros, true);
          case MAX_DRAWDOWN -> byValue(candidate -> indicators(candidate).maxDrawdown(), true);
        };
    return byIndicator
        .thenComparing(candidate -> candidate.instrument().name().toLowerCase(Locale.ROOT))
        .thenComparing(candidate -> candidate.instrument().listing().isin().value());
  }

  private static IndicatorSet indicators(InstrumentIndicators candidate) {
    return candidate.indicators().indicators();
  }

  /** Amounts in another currency are not ranked with amounts in euros: they come last. */
  private static Indicator amountInEuros(InstrumentIndicators candidate) {
    return EUR.equals(candidate.instrument().currency())
        ? indicators(candidate).averageDailyTradedAmount()
        : Indicator.NOT_COMPUTABLE;
  }

  /** Instruments without the indicator last, whatever the direction. */
  private static Comparator<InstrumentIndicators> byValue(
      Function<InstrumentIndicators, Indicator> indicator, boolean descending) {
    return (first, second) -> {
      BigDecimal a = indicator.apply(first).value();
      BigDecimal b = indicator.apply(second).value();
      if (a == null || b == null) {
        return Boolean.compare(a == null, b == null);
      }
      return descending ? b.compareTo(a) : a.compareTo(b);
    };
  }
}
