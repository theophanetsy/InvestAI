package dev.investai.portfolio.api;

import dev.investai.portfolio.domain.CurvePoint;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One session of the one-year curve, base 100 on the first session (decimal strings, scale 2). A
 * value is absent on a session without close: a gap in the curve, never interpolated.
 *
 * @param date the session
 * @param portfolio the current portfolio as if held for a year
 * @param msciWorld the MSCI World ETF (CW8); absent on every session when its history is
 *     insufficient
 * @param cac40 the CAC 40 ETF (CAC), likewise
 */
public record CurvePointResponse(
    LocalDate date,
    @Schema(example = "104.35") @Nullable String portfolio,
    @Schema(example = "102.10") @Nullable String msciWorld,
    @Schema(example = "98.75") @Nullable String cac40) {

  static CurvePointResponse from(CurvePoint point) {
    return new CurvePointResponse(
        point.date(), text(point.portfolio()), text(point.msciWorld()), text(point.cac40()));
  }

  private static @Nullable String text(@Nullable BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }
}
