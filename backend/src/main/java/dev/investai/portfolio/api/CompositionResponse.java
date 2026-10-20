package dev.investai.portfolio.api;

import dev.investai.market.api.IndicatorResponse;
import dev.investai.portfolio.domain.Composition;

/**
 * Composition of the declared PEA. Shares of the total value (cash included) in percent; {@code
 * NOT_COMPUTABLE} when the total is zero or, for the cash, when it is not declared.
 *
 * @param lineCount number of declared lines, valued or not
 * @param largestLinePercent weight of the largest line
 * @param herfindahlIndex concentration of the invested part (cash excluded): sum of the squared
 *     weights, 1 for a single line, 1/n for n equal lines (scale 4)
 * @param cashPercent share of the cash
 * @param etfPercent share of the ETFs
 * @param stockPercent share of the shares
 * @param otherPercent share of the other funds and instruments
 */
public record CompositionResponse(
    int lineCount,
    IndicatorResponse largestLinePercent,
    IndicatorResponse herfindahlIndex,
    IndicatorResponse cashPercent,
    IndicatorResponse etfPercent,
    IndicatorResponse stockPercent,
    IndicatorResponse otherPercent) {

  static CompositionResponse from(Composition composition) {
    return new CompositionResponse(
        composition.lineCount(),
        IndicatorResponse.from(composition.largestLinePercent()),
        IndicatorResponse.from(composition.herfindahlIndex()),
        IndicatorResponse.from(composition.cashPercent()),
        IndicatorResponse.from(composition.etfPercent()),
        IndicatorResponse.from(composition.stockPercent()),
        IndicatorResponse.from(composition.otherPercent()));
  }
}
