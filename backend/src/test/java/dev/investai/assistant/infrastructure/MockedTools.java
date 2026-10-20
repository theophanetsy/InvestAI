package dev.investai.assistant.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.investai.assistant.application.AdviceAllocation;
import dev.investai.assistant.application.AdviceProfile;
import dev.investai.market.application.BenchmarkQueries;
import dev.investai.market.application.IndicatorQueries;
import dev.investai.market.application.MarketFreshness;
import dev.investai.market.application.MarketQueries;
import dev.investai.portfolio.application.PortfolioQueries;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import java.time.Clock;
import java.util.List;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The advice tools on mocked use cases, declared and wrapped like {@link McpToolsConfiguration}
 * does, to call them without Spring nor database. The mocks answer empty values.
 */
final class MockedTools {

  private final List<SyncToolSpecification> specifications;

  MockedTools() {
    var market = mock(MarketQueries.class);
    var indicators = mock(IndicatorQueries.class);
    var freshness = mock(MarketFreshness.class);
    when(freshness.current()).thenReturn(new MarketFreshness.Freshness(null, null));
    var clock = Clock.systemUTC();
    List<Object> tools =
        List.of(
            new InvestorProfileTool(mock(AdviceProfile.class)),
            new PortfolioTool(mock(PortfolioQueries.class), clock),
            new SearchInstrumentsTool(market),
            new InstrumentTool(indicators),
            new PriceHistoryTool(market),
            new ScreenInstrumentsTool(indicators, freshness),
            new BenchmarksTool(mock(BenchmarkQueries.class)),
            new AllocationTool(mock(AdviceAllocation.class), clock));
    var logging = new ToolCallLogging(JsonMapper.builder().build());
    specifications =
        SyncMcpAnnotationProviders.statelessToolSpecifications(tools).stream()
            .map(StrictInputs::declare)
            .map(logging::wrap)
            .toList();
  }

  /**
   * Returns a tool as the server serves it: strict input schema, logged, errors made generic.
   *
   * @param name its name
   * @return its specification
   */
  SyncToolSpecification tool(String name) {
    return specifications.stream()
        .filter(specification -> specification.tool().name().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
