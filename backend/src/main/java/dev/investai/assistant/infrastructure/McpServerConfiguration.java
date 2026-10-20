package dev.investai.assistant.infrastructure;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import java.util.List;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * The stateless MCP server on {@code /mcp} (spec 3.1, criteria 9 and 32), in place of {@code
 * McpServerStatelessAutoConfiguration} of Spring AI, excluded in {@code application.properties}.
 *
 * <p>Built like that auto-configuration (same properties, transport of the starter, immediate
 * execution on the request thread), with one difference: the SDK does not validate the arguments
 * against the declared input schema. That validation answers before the tool with its own message
 * and logs it, the names of unknown arguments (text of the client) included; every tool checks its
 * arguments itself ({@link ToolArguments}), answers {@code tool.invalid-argument} with the name of
 * the parameter and nothing else, and is logged once by {@link ToolCallLogging}. Instructions and
 * prompts come from the versioned texts of {@link AdviceGuidance}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("spring.ai.mcp.server.enabled")
@EnableConfigurationProperties(McpServerProperties.class)
public class McpServerConfiguration {

  /** Bean of the prompts. */
  static final String PROMPTS = "investaiPrompts";

  /**
   * The MCP server.
   *
   * @param transport the stateless Streamable HTTP transport of the starter
   * @param properties {@code spring.ai.mcp.server.*}
   * @param tools the allow-list of tools
   * @param prompts the allow-list of prompts
   * @param guidance the instructions of the server
   * @return the server
   */
  @Bean
  McpStatelessSyncServer mcpStatelessSyncServer(
      McpStatelessServerTransport transport,
      McpServerProperties properties,
      @Qualifier(McpToolsConfiguration.TOOLS) List<SyncToolSpecification> tools,
      @Qualifier(PROMPTS) List<SyncPromptSpecification> prompts,
      AdviceGuidance guidance) {
    var capabilities = ServerCapabilities.builder();
    var server =
        McpServer.sync(transport)
            .serverInfo(
                Implementation.builder(properties.getName(), properties.getVersion()).build())
            .instructions(guidance.serverInstructions())
            .requestTimeout(properties.getRequestTimeout())
            .immediateExecution(true)
            .validateToolInputs(false);
    if (properties.getCapabilities().isTool()) {
      capabilities.tools(false);
      server.tools(tools);
    }
    if (properties.getCapabilities().isPrompt()) {
      capabilities.prompts(false);
      server.prompts(prompts);
    }
    return server.capabilities(capabilities.build()).build();
  }

  /**
   * The prompts served by the stateless MCP server.
   *
   * @param guidance the versioned texts of the advice
   * @param json the MCP JSON mapper
   * @return the prompt {@code conseil-placement}, logged
   */
  @Bean(PROMPTS)
  List<SyncPromptSpecification> investaiPrompts(
      AdviceGuidance guidance, @Qualifier("mcpServerJsonMapper") JsonMapper json) {
    return List.of(new ToolCallLogging(json).wrap(AdvicePrompt.specification(guidance)));
  }
}
