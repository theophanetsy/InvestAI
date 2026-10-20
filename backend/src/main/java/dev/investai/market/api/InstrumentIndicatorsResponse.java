package dev.investai.market.api;

import dev.investai.market.domain.InstrumentIndicators;
import java.time.Instant;

/**
 * An instrument with its indicators, as stored by the daily computation.
 *
 * @param instrument the instrument, with its PEA eligibility
 * @param indicators its indicators
 * @param computedAt when they were computed
 */
public record InstrumentIndicatorsResponse(
    InstrumentSummaryResponse instrument, IndicatorsResponse indicators, Instant computedAt) {

  static InstrumentIndicatorsResponse from(InstrumentIndicators indicators) {
    return new InstrumentIndicatorsResponse(
        InstrumentSummaryResponse.from(indicators.instrument()),
        IndicatorsResponse.from(
            indicators.indicators().indicators(), indicators.instrument().currency()),
        indicators.indicators().computedAt());
  }
}
