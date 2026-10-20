package dev.investai.assistant.infrastructure;

import dev.investai.assistant.application.AssistantHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * MCP tool {@code getHealth} (spec 3.0, criterion 12): checks that the server answers and tells how
 * recent the data is. Read-only, no argument, no personal data.
 */
@Component
public class HealthTool {

  /** Name of the tool, as listed by {@code tools/list}. */
  public static final String NAME = "getHealth";

  private static final Logger LOG = LoggerFactory.getLogger(HealthTool.class);
  private static final String TITLE = "Santé d'InvestAI";

  private final AssistantHealth health;

  /**
   * Creates the tool.
   *
   * @param health the use case
   */
  public HealthTool(AssistantHealth health) {
    this.health = health;
  }

  /**
   * Reports the version of the application and the dates of the data.
   *
   * @return the health report, as structured content
   */
  @McpTool(
      name = NAME,
      title = TITLE,
      description =
          """
          Vérifie que le serveur InvestAI répond et indique la fraîcheur de ses données : \
          version de l'application, séance de la dernière clôture en base (closesAsOf), séance \
          de référence et horodatage du dernier calcul des indicateurs (indicatorsAsOf, \
          indicatorsComputedAt). status vaut READY quand les clôtures et les indicateurs \
          existent, NO_CLOSES sans aucune clôture, INDICATORS_NOT_COMPUTED avant le premier \
          calcul. À appeler en début de session de conseil ; citer ces dates. Lecture seule, \
          aucune donnée personnelle.""",
      annotations =
          @McpAnnotations(
              title = TITLE,
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false),
      generateOutputSchema = true)
  public HealthResult getHealth() {
    try {
      return HealthResult.from(health.report());
    } catch (DataAccessException e) {
      // The annotation callback keeps only the message; ToolCallLogging answers tool.failed.
      LOG.error("MCP tool getHealth: database unavailable", e);
      throw e;
    }
  }
}
