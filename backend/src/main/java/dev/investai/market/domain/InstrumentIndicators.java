package dev.investai.market.domain;

/**
 * An instrument with its stored indicators.
 *
 * @param instrument the instrument, with its PEA eligibility
 * @param indicators its latest indicators
 */
public record InstrumentIndicators(Instrument instrument, StoredIndicators indicators) {}
