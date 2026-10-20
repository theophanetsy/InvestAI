package dev.investai.assistant.infrastructure;

import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import java.util.List;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * The allow-list of MCP tools (ADR-0010, spec 3.0, criteria 6 and 13): every tool is listed here,
 * explicitly; annotations are never scanned. Each one goes through {@link ToolCallLogging}. Active
 * only when the MCP server is (see {@code auth.infrastructure.McpEndpointActivation}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("spring.ai.mcp.server.enabled")
public class McpToolsConfiguration {

  /**
   * The tools served by the stateless MCP server.
   *
   * @param health {@code getHealth}
   * @param json the MCP JSON mapper
   * @return the tools, logged
   */
  @Bean
  List<SyncToolSpecification> investaiTools(
      HealthTool health, @Qualifier("mcpServerJsonMapper") JsonMapper json) {
    var logging = new ToolCallLogging(json);
    return SyncMcpAnnotationProviders.statelessToolSpecifications(List.of(health)).stream()
        .map(logging::wrap)
        .toList();
  }
}
