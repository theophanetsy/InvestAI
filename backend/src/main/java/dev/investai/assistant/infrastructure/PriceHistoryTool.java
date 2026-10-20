package dev.investai.assistant.infrastructure;

import dev.investai.market.application.MarketQueries;
import dev.investai.market.application.PriceHistory;
import dev.investai.market.domain.IndicatorWindow;
import dev.investai.market.domain.InstrumentId;
import dev.investai.shared.domain.ResourceNotFoundException;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.stereotype.Component;

/**
 * MCP tool {@code getPriceHistory} (spec 3.1, criteria 21 and 22): the raw closes behind a
 * surprising indicator (split, bonus shares, outlier quote, gaps).
 */
@Component
public class PriceHistoryTool {

  /** Name of the tool, as listed by {@code tools/list}. */
  public static final String NAME = "getPriceHistory";

  /** The period parameter. */
  static final String PERIOD = "period";

  private static final String TITLE = "Historique des clôtures";

  private final MarketQueries market;

  /**
   * Creates the tool.
   *
   * @param market the stored closes
   */
  public PriceHistoryTool(MarketQueries market) {
    this.market = market;
  }

  /**
   * Declares the parameters, in place of the schema generated from the method (which knows neither
   * bounds nor patterns nor refused properties; see {@link McpToolsConfiguration}).
   *
   * @return the strict input schema
   */
  static InputSchema inputSchema() {
    return new InputSchema()
        .instrumentId(InstrumentTool.INSTRUMENT_ID, InstrumentTool.INSTRUMENT_ID_DESCRIPTION)
        .constant(
            PERIOD,
            "Période en séances Euronext (21, 63, 126 ou 252) finissant à la dernière clôture de"
                + " la valeur.",
            IndicatorWindow.values(),
            IndicatorWindow.THREE_MONTHS);
  }

  /**
   * Returns the raw closes of a period.
   *
   * @param request the call, with {@code instrumentId} and optionally {@code period}
   * @return the closes
   * @throws ToolCallRejected when an argument is invalid or the instrument unknown
   */
  @McpTool(
      name = NAME,
      title = TITLE,
      description =
          """
          Renvoie les clôtures quotidiennes brutes d'une valeur sur 1, 3, 6 ou 12 mois (253 \
          clôtures au plus), avec le nombre de séances de la période et le nombre de séances sans \
          clôture. Sert à vérifier un indicateur surprenant : saut brutal du cours (division, \
          attribution gratuite, cotation aberrante) ou trous dans l'historique. Ne pas en tirer de \
          nouveaux indicateurs chiffrés : ceux de getInstrument font foi. Lire dataLimits. Erreurs \
          possibles : tool.invalid-argument, instrument.not-found. Lecture seule.""",
      annotations =
          @McpAnnotations(
              title = TITLE,
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false),
      generateOutputSchema = true)
  public PriceHistoryResult getPriceHistory(CallToolRequest request) throws ToolCallRejected {
    ToolArguments arguments = ToolArguments.of(request, inputSchema().names());
    InstrumentId id = arguments.instrumentId(InstrumentTool.INSTRUMENT_ID);
    IndicatorWindow period =
        arguments.constant(PERIOD, IndicatorWindow.class, IndicatorWindow.THREE_MONTHS);
    return PriceHistoryResult.of(history(id, period));
  }

  private PriceHistory history(InstrumentId id, IndicatorWindow period) throws ToolCallRejected {
    try {
      return market.history(id, period);
    } catch (ResourceNotFoundException e) {
      throw ToolCallRejected.instrumentNotFound(e);
    }
  }
}
