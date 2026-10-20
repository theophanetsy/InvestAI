package dev.investai.portfolio.api;

import dev.investai.market.api.IndicatorsResponse;
import dev.investai.market.api.InstrumentSummaryResponse;
import dev.investai.portfolio.domain.LineExclusion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A line of the declared PEA with the indicators of its instrument.
 *
 * @param lineId identifier of the line
 * @param instrument the instrument, with its PEA eligibility
 * @param weightPercent share of the total value (cash included), scale 2; absent without close
 * @param indicators indicators of the instrument; absent when no close is stored at all
 * @param exclusion why the line is left out of the risk of the portfolio (volatility, maximum
 *     drawdown, curve, correlations); absent when it is kept
 */
public record LineIndicatorsResponse(
    UUID lineId,
    InstrumentSummaryResponse instrument,
    @Schema(example = "42.10") @Nullable String weightPercent,
    @Nullable IndicatorsResponse indicators,
    @Nullable LineExclusion exclusion) {}
