package dev.investai.assistant.domain;

import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.Instrument;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * A line proposed by the model, with what the application knows about its instrument.
 *
 * @param instrument the instrument, with its PEA eligibility
 * @param weightPercent share of the sum to invest, in percent, positive
 * @param lastClose its latest close, {@code null} when none is stored
 */
public record AllocationCandidate(
    Instrument instrument, BigDecimal weightPercent, @Nullable DailyClose lastClose) {}
