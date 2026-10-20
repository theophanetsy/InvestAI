package dev.investai.portfolio.api;

import dev.investai.market.api.IndicatorResponse;
import dev.investai.market.api.IndicatorsResponse;
import dev.investai.market.api.InstrumentSummaryResponse;
import dev.investai.market.domain.IndicatorSet;
import dev.investai.market.domain.Instrument;
import dev.investai.portfolio.application.PortfolioIndicators;
import dev.investai.portfolio.domain.Amounts;
import dev.investai.portfolio.domain.PortfolioRisk;
import dev.investai.portfolio.domain.ValuedLine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Indicators of the declared PEA, computed on demand from the stored closes.
 *
 * @param asOf last session of the closes used; absent when no close is stored at all
 * @param total value of the valued lines plus the cash, in euros, scale 2
 * @param currency always {@code EUR}
 * @param composition shares and concentration
 * @param volatility annualised volatility of the current portfolio as if held for a year (cash
 *     excluded), in percent
 * @param maxDrawdown largest fall of the current portfolio as if held for a year, in percent
 * @param lines the lines, by instrument name, with the indicators of their instrument
 * @param curve the current portfolio and the two benchmark ETFs over the year, base 100; empty when
 *     the history of the portfolio is insufficient
 * @param correlations correlations between the lines kept in the risk
 */
@Schema(
    description =
        "Indicateurs du portefeuille déclaré. Volatilité, pire baisse, courbe et corrélations :"
            + " portefeuille actuel (quantités d'aujourd'hui, espèces exclues) comme s'il avait été"
            + " détenu depuis un an, sans tenir compte des achats et ventes réels. Lignes sans"
            + " cours en euros ou avec moins de 80 % des clôtures de l'année exclues et signalées"
            + " (exclusion). Clôtures brutes : hors dividendes, sans ajustement des divisions ni"
            + " des attributions gratuites.")
public record PortfolioIndicatorsResponse(
    @Nullable LocalDate asOf,
    @Schema(example = "12500.40") String total,
    String currency,
    CompositionResponse composition,
    IndicatorResponse volatility,
    IndicatorResponse maxDrawdown,
    List<LineIndicatorsResponse> lines,
    List<CurvePointResponse> curve,
    CorrelationMatrixResponse correlations) {

  /** Defensive copies. */
  public PortfolioIndicatorsResponse {
    lines = List.copyOf(lines);
    curve = List.copyOf(curve);
  }

  static PortfolioIndicatorsResponse from(PortfolioIndicators indicators) {
    PortfolioRisk risk = indicators.risk();
    return new PortfolioIndicatorsResponse(
        indicators.asOf(),
        indicators.overview().valuation().total().toPlainString(),
        Amounts.EUR.getCurrencyCode(),
        CompositionResponse.from(indicators.composition()),
        IndicatorResponse.from(risk.volatility()),
        IndicatorResponse.from(risk.maxDrawdown()),
        indicators.overview().valuation().lines().stream()
            .map(line -> line(line, indicators))
            .toList(),
        risk.curve().stream().map(CurvePointResponse::from).toList(),
        CorrelationMatrixResponse.from(risk.correlations()));
  }

  private static LineIndicatorsResponse line(ValuedLine line, PortfolioIndicators indicators) {
    Instrument instrument = indicators.overview().instrumentOf(line.line());
    IndicatorSet set = indicators.lineIndicators().get(instrument.id());
    BigDecimal weight = line.weightPercent();
    return new LineIndicatorsResponse(
        line.line().id().value(),
        InstrumentSummaryResponse.from(instrument),
        weight == null ? null : weight.toPlainString(),
        set == null ? null : IndicatorsResponse.from(set, instrument.currency()),
        indicators.risk().exclusions().get(line.line().id()));
  }
}
