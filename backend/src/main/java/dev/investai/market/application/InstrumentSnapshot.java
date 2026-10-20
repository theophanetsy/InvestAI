package dev.investai.market.application;

import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.StoredIndicators;
import org.jspecify.annotations.Nullable;

/**
 * Everything stored about an instrument: its latest close and its indicators of the daily
 * computation (MCP tools {@code getInstrument} and {@code getBenchmarks}, spec 3.1).
 *
 * @param instrument the instrument, with its PEA eligibility
 * @param lastClose its most recent close, {@code null} when none is stored
 * @param indicators its stored indicators, {@code null} before the first computation that saw it
 */
public record InstrumentSnapshot(
    Instrument instrument, @Nullable DailyClose lastClose, @Nullable StoredIndicators indicators) {}
