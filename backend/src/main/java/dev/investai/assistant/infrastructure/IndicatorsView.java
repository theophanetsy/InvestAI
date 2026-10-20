package dev.investai.assistant.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.investai.market.domain.BenchmarkComparison;
import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.IndicatorSet;
import dev.investai.market.domain.IndicatorStatus;
import dev.investai.market.domain.IndicatorWindow;
import java.math.BigDecimal;
import java.util.Currency;
import org.jspecify.annotations.Nullable;

/**
 * Every indicator of an instrument as of its session (spec 3.1, criterion 20), shared by {@code
 * getInstrument}, {@code getBenchmarks} and the lines of {@code getPortfolio}. Raw closes,
 * performances excluding dividends.
 *
 * @param performancePercent change of the close over each window
 * @param volatilityPercent annualised volatility over one year
 * @param maxDrawdownPercent largest fall over one year, zero or negative
 * @param msciWorld compared with the MSCI World ETF (CW8)
 * @param cac40 compared with the CAC 40 ETF (CAC)
 * @param averageDailyTradedAmount mean of close × volume over three months (Euronext volumes)
 */
public record IndicatorsView(
    Performance performancePercent,
    IndicatorValue volatilityPercent,
    IndicatorValue maxDrawdownPercent,
    Comparison msciWorld,
    Comparison cac40,
    TradedAmount averageDailyTradedAmount) {

  static IndicatorsView of(IndicatorSet set, Currency currency) {
    return new IndicatorsView(
        new Performance(
            IndicatorValue.of(set.performance(IndicatorWindow.ONE_MONTH)),
            IndicatorValue.of(set.performance(IndicatorWindow.THREE_MONTHS)),
            IndicatorValue.of(set.performance(IndicatorWindow.SIX_MONTHS)),
            IndicatorValue.of(set.performance(IndicatorWindow.ONE_YEAR))),
        IndicatorValue.of(set.volatility()),
        IndicatorValue.of(set.maxDrawdown()),
        Comparison.of(set.msciWorld()),
        Comparison.of(set.cac40()),
        TradedAmount.of(set.averageDailyTradedAmount(), currency));
  }

  /**
   * Performance over each window, in percent, excluding dividends.
   *
   * @param oneMonth 21 sessions
   * @param threeMonths 63 sessions
   * @param sixMonths 126 sessions
   * @param oneYear 252 sessions
   */
  public record Performance(
      IndicatorValue oneMonth,
      IndicatorValue threeMonths,
      IndicatorValue sixMonths,
      IndicatorValue oneYear) {}

  /**
   * The instrument compared with a benchmark ETF over one year.
   *
   * @param outperformancePoints one-year performance minus that of the benchmark, in points
   * @param correlation correlation of the daily returns, between -1 and 1
   * @param beta sensitivity of the daily returns to those of the benchmark
   */
  public record Comparison(
      IndicatorValue outperformancePoints, IndicatorValue correlation, IndicatorValue beta) {

    static Comparison of(BenchmarkComparison comparison) {
      return new Comparison(
          IndicatorValue.of(comparison.outperformance()),
          IndicatorValue.of(comparison.correlation()),
          IndicatorValue.of(comparison.beta()));
    }
  }

  /**
   * Average daily traded amount, an indicator in the currency of the instrument.
   *
   * @param status availability of the figure
   * @param value plain decimal string, present exactly when available
   * @param currency currency of the amount
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record TradedAmount(IndicatorStatus status, @Nullable String value, String currency) {

    static TradedAmount of(Indicator indicator, Currency currency) {
      BigDecimal value = indicator.value();
      return new TradedAmount(
          indicator.status(),
          value == null ? null : value.toPlainString(),
          currency.getCurrencyCode());
    }
  }
}
