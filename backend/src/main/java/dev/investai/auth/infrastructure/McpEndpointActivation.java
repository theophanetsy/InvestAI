package dev.investai.auth.infrastructure;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Mounts {@code /mcp} only when {@code INVESTAI_MCP_TOKEN_HASH} is set (ADR-0010, spec 3.0,
 * criterion 7): the token is the only switch of the MCP server. Spring AI's starter is turned on
 * and off by {@code spring.ai.mcp.server.enabled}, which a property file cannot derive from another
 * property being empty, hence this post-processor. It runs after the configuration files (including
 * {@code infra/.env} in the local profile) and overrides any value set elsewhere.
 */
public class McpEndpointActivation implements EnvironmentPostProcessor, Ordered {

  /** Property holding the SHA-256 of the MCP token ({@code INVESTAI_MCP_TOKEN_HASH}). */
  static final String TOKEN_HASH = "investai.mcp.token-hash";

  /** Switch of Spring AI's MCP server auto-configuration. */
  static final String SERVER_ENABLED = "spring.ai.mcp.server.enabled";

  /** Startup warning when the server stays off. */
  static final String DISABLED_WARNING =
      "MCP server disabled: INVESTAI_MCP_TOKEN_HASH is not set (see docs/setup.md)";

  private static final String PROPERTY_SOURCE = "investaiMcpActivation";

  private final DeferredLogFactory logs;

  /**
   * Creates the post-processor (instantiated by Spring Boot from {@code spring.factories}).
   *
   * @param logs logs written once the logging system is ready
   */
  public McpEndpointActivation(DeferredLogFactory logs) {
    this.logs = logs;
  }

  /**
   * Tells whether the MCP token hash is configured.
   *
   * @param environment the environment
   * @return {@code true} when {@code investai.mcp.token-hash} has a value
   */
  static boolean isConfigured(Environment environment) {
    return StringUtils.hasText(environment.getProperty(TOKEN_HASH));
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    boolean configured = isConfigured(environment);
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource(PROPERTY_SOURCE, Map.of(SERVER_ENABLED, configured)));
    if (!configured) {
      logs.getLog(McpEndpointActivation.class).warn(DISABLED_WARNING);
    }
  }

  @Override
  public int getOrder() {
    return LOWEST_PRECEDENCE;
  }
}
