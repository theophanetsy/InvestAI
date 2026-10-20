package dev.investai.portfolio.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One session of the one-year curve, base 100 on its first session; a missing close is a gap, never
 * interpolated.
 *
 * @param date the session
 * @param portfolio the current portfolio as if held for a year, {@code null} when a line has no
 *     close that day
 * @param msciWorld the MSCI World ETF, {@code null} without close or when its history is
 *     insufficient
 * @param cac40 the CAC 40 ETF, likewise
 */
public record CurvePoint(
    LocalDate date,
    @Nullable BigDecimal portfolio,
    @Nullable BigDecimal msciWorld,
    @Nullable BigDecimal cac40) {}
