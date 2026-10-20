package dev.investai.assistant.infrastructure;

import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * The allow-lists of MCP tools and prompts (ADR-0010; spec 3.0, criteria 6 and 13; spec 3.1,
 * criteria 11 and 32): every one is listed here, explicitly; annotations are never scanned. Each
 * one goes through {@link ToolCallLogging}. Active only when the MCP server is (see {@code
 * auth.infrastructure.McpEndpointActivation}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("spring.ai.mcp.server.enabled")
public class McpToolsConfiguration {

  /** Bean of the tools, read by {@link McpServerConfiguration}. */
  static final String TOOLS = "investaiTools";

  /**
   * The tools about the owner.
   *
   * @param profile {@code getInvestorProfile}
   * @param portfolio {@code getPortfolio}
   * @param allocation {@code computeAllocation}
   * @return the group
   */
  @Bean
  OwnerTools ownerTools(
      InvestorProfileTool profile, PortfolioTool portfolio, AllocationTool allocation) {
    return new OwnerTools(profile, portfolio, allocation);
  }

  /**
   * The tools about the market.
   *
   * @param search {@code searchInstruments}
   * @param instrument {@code getInstrument}
   * @param history {@code getPriceHistory}
   * @param screen {@code screenInstruments}
   * @param benchmarks {@code getBenchmarks}
   * @return the group
   */
  @Bean
  MarketTools marketTools(
      SearchInstrumentsTool search,
      InstrumentTool instrument,
      PriceHistoryTool history,
      ScreenInstrumentsTool screen,
      BenchmarksTool benchmarks) {
    return new MarketTools(search, instrument, history, screen, benchmarks);
  }

  /**
   * The tools served by the stateless MCP server, in the order of {@code tools/list}.
   *
   * @param health {@code getHealth}
   * @param owner the tools about the owner
   * @param market the tools about the market
   * @param json the MCP JSON mapper
   * @return the tools, with their strict input schema, logged
   */
  @Bean(TOOLS)
  List<SyncToolSpecification> investaiTools(
      HealthTool health,
      OwnerTools owner,
      MarketTools market,
      @Qualifier("mcpServerJsonMapper") JsonMapper json) {
    var logging = new ToolCallLogging(json);
    List<Object> tools = new ArrayList<>();
    tools.add(health);
    tools.add(owner.profile());
    tools.add(owner.portfolio());
    tools.addAll(market.all());
    tools.add(owner.allocation());
    return SyncMcpAnnotationProviders.statelessToolSpecifications(tools).stream()
        .map(StrictInputs::declare)
        .map(logging::wrap)
        .toList();
  }

  /**
   * The tools about the owner (profile, portfolio, allocation).
   *
   * @param profile {@code getInvestorProfile}
   * @param portfolio {@code getPortfolio}
   * @param allocation {@code computeAllocation}
   */
  record OwnerTools(
      InvestorProfileTool profile, PortfolioTool portfolio, AllocationTool allocation) {}

  /**
   * The tools about the market, in the order of {@code tools/list}.
   *
   * @param search {@code searchInstruments}
   * @param instrument {@code getInstrument}
   * @param history {@code getPriceHistory}
   * @param screen {@code screenInstruments}
   * @param benchmarks {@code getBenchmarks}
   */
  record MarketTools(
      SearchInstrumentsTool search,
      InstrumentTool instrument,
      PriceHistoryTool history,
      ScreenInstrumentsTool screen,
      BenchmarksTool benchmarks) {

    List<Object> all() {
      return List.of(search, instrument, history, screen, benchmarks);
    }
  }
}
