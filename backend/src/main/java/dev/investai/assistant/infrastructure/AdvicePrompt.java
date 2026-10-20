package dev.investai.assistant.infrastructure;

import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;

/**
 * The MCP prompt {@code conseil-placement} (spec 3.1, criterion 32): the whole advice method, that
 * the owner picks in the client ({@code /mcp__investai__conseil-placement} in Claude Code). No
 * argument: nothing is ever inserted in its text.
 */
final class AdvicePrompt {

  /** Name of the prompt, as listed by {@code prompts/list}. */
  static final String NAME = "conseil-placement";

  private static final String TITLE = "Conseil de placement";
  private static final String DESCRIPTION =
      "Méthode complète pour proposer un placement de la somme du profil sur le PEA, en actions et"
          + " ETF de Paris, avec les outils d'InvestAI.";

  private AdvicePrompt() {}

  /**
   * Declares the prompt.
   *
   * @param guidance the versioned texts
   * @return the prompt, answering always the same message
   */
  static SyncPromptSpecification specification(AdviceGuidance guidance) {
    Prompt prompt =
        Prompt.builder(NAME).title(TITLE).description(DESCRIPTION).arguments(List.of()).build();
    GetPromptResult result =
        GetPromptResult.builder(
                List.of(
                    PromptMessage.builder(
                            Role.USER, TextContent.builder(guidance.advicePrompt()).build())
                        .build()))
            .description(DESCRIPTION)
            .build();
    return new SyncPromptSpecification(prompt, (context, request) -> result);
  }
}
