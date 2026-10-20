package dev.investai.assistant.domain;

import dev.investai.market.domain.InstrumentId;
import java.math.BigDecimal;

/**
 * Two lines of the portfolio and the correlation of their daily returns over one year.
 *
 * @param first the first instrument, in the order of the matrix
 * @param second the second instrument
 * @param correlation between -1 and 1
 */
public record CorrelatedPair(InstrumentId first, InstrumentId second, BigDecimal correlation) {}
