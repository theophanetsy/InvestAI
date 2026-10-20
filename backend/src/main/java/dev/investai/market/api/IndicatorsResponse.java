package dev.investai.market.api;

import dev.investai.market.domain.IndicatorSet;
import dev.investai.market.domain.IndicatorWindow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Currency;

/**
 * Indicators of an instrument on its raw closes, as of a session. Percentages at scale 2. Shared
 * with the portfolio API.
 *
 * @param asOf last session of every window
 * @param closesOneYear closes present among the 253 sessions of the one-year window
 * @param performanceOneMonth change of the close over 21 sessions, in percent, excluding dividends
 * @param performanceThreeMonths over 63 sessions
 * @param performanceSixMonths over 126 sessions
 * @param performanceOneYear over 252 sessions
 * @param volatility annualised volatility of the daily returns over one year, in percent
 * @param maxDrawdown largest fall from a high to a later low over one year, in percent (≤ 0)
 * @param msciWorld compared with the MSCI World ETF (CW8)
 * @param cac40 compared with the CAC 40 ETF (CAC)
 * @param averageDailyTradedAmount mean Euronext close × volume over 63 sessions, in {@code
 *     currency}
 * @param currency currency of the instrument (ISO 4217)
 */
@Schema(
    description =
        "Indicateurs calculés sur les clôtures brutes. Performance hors dividendes. Aucun"
            + " ajustement des divisions ni des attributions gratuites : l'année d'une telle"
            + " opération, la performance et la pire baisse sont faussées par une chute"
            + " artificielle du cours.")
public record IndicatorsResponse(
    LocalDate asOf,
    @Schema(minimum = "0", maximum = "253") int closesOneYear,
    IndicatorResponse performanceOneMonth,
    IndicatorResponse performanceThreeMonths,
    IndicatorResponse performanceSixMonths,
    IndicatorResponse performanceOneYear,
    IndicatorResponse volatility,
    IndicatorResponse maxDrawdown,
    BenchmarkComparisonResponse msciWorld,
    BenchmarkComparisonResponse cac40,
    IndicatorResponse averageDailyTradedAmount,
    String currency) {

  /**
   * Converts the domain value; also used by the portfolio API.
   *
   * @param set the indicators
   * @param currency currency of the instrument
   * @return the response
   */
  public static IndicatorsResponse from(IndicatorSet set, Currency currency) {
    return new IndicatorsResponse(
        set.asOf(),
        set.closesOneYear(),
        IndicatorResponse.from(set.performance(IndicatorWindow.ONE_MONTH)),
        IndicatorResponse.from(set.performance(IndicatorWindow.THREE_MONTHS)),
        IndicatorResponse.from(set.performance(IndicatorWindow.SIX_MONTHS)),
        IndicatorResponse.from(set.performance(IndicatorWindow.ONE_YEAR)),
        IndicatorResponse.from(set.volatility()),
        IndicatorResponse.from(set.maxDrawdown()),
        BenchmarkComparisonResponse.from(set.msciWorld()),
        BenchmarkComparisonResponse.from(set.cac40()),
        IndicatorResponse.from(set.averageTradedAmount()),
        currency.getCurrencyCode());
  }
}
