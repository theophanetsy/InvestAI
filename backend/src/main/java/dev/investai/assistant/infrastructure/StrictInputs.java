package dev.investai.assistant.infrastructure;

import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.Map;

/**
 * The strict input schemas declared by the tools (spec 3.1, criterion 9), in place of those that
 * Spring AI generates from their method: the generator knows neither bounds nor patterns nor
 * refused properties. The checks of {@link ToolArguments} remain the rule.
 */
final class StrictInputs {

  private StrictInputs() {}

  /**
   * Replaces the input schema generated from the method by the strict one the tool declares. The
   * schema of {@code getHealth} (phase 3.0) stays as generated.
   */
  static SyncToolSpecification declare(SyncToolSpecification specification) {
    Tool tool = specification.tool();
    InputSchema strict = strictInputs().get(tool.name());
    if (strict == null) {
      return specification;
    }
    Tool declared =
        Tool.builder(tool.name(), strict.build())
            .title(tool.title())
            .description(tool.description())
            .outputSchema(tool.outputSchema())
            .annotations(tool.annotations())
            .meta(tool.meta())
            .build();
    return new SyncToolSpecification(declared, specification.callHandler());
  }

  private static Map<String, InputSchema> strictInputs() {
    return Map.of(
        InvestorProfileTool.NAME, InvestorProfileTool.inputSchema(),
        PortfolioTool.NAME, PortfolioTool.inputSchema(),
        SearchInstrumentsTool.NAME, SearchInstrumentsTool.inputSchema(),
        InstrumentTool.NAME, InstrumentTool.inputSchema(),
        PriceHistoryTool.NAME, PriceHistoryTool.inputSchema(),
        ScreenInstrumentsTool.NAME, ScreenInstrumentsTool.inputSchema(),
        BenchmarksTool.NAME, BenchmarksTool.inputSchema(),
        AllocationTool.NAME, AllocationTool.inputSchema());
  }
}
