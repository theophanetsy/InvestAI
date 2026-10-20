package dev.investai.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.investai.assistant.infrastructure.HealthTool;
import dev.investai.assistant.infrastructure.McpToolsConfiguration;
import dev.investai.support.FixtureCredentials;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import org.apache.commons.logging.Log;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStatelessAutoConfiguration;
import org.springframework.ai.mcp.server.webmvc.autoconfigure.McpServerStatelessWebMvcAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.servlet.function.RouterFunction;

/**
 * {@code /mcp} is mounted only when the token hash is set (spec 3.0, criterion 7), proven on Spring
 * AI's real auto-configurations with the application's MCP properties.
 */
class McpEndpointActivationTest {

  private static final String INVESTAI_TOOLS = "investaiTools";

  private final Log log = mock();
  private final McpEndpointActivation activation = new McpEndpointActivation(supplier -> log);

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  McpServerJsonMapperAutoConfiguration.class,
                  McpServerStatelessAutoConfiguration.class,
                  McpServerStatelessWebMvcAutoConfiguration.class))
          .withPropertyValues(
              "spring.ai.mcp.server.protocol=STATELESS",
              "spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp")
          .withUserConfiguration(McpToolsConfiguration.class, TokenProbe.class)
          .withBean(HealthTool.class, () -> mock(HealthTool.class))
          .withInitializer(this::activate);

  @Test
  void withoutTokenHashNothingIsMountedAndAWarningIsLogged() {
    runner
        .withPropertyValues(McpEndpointActivation.TOKEN_HASH + "=")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(McpStatelessSyncServer.class);
              assertThat(context).doesNotHaveBean(RouterFunction.class);
              assertThat(context).doesNotHaveBean(INVESTAI_TOOLS);
              assertThat(context).doesNotHaveBean(TokenProbe.PROBE);
            });

    verify(log).warn(McpEndpointActivation.DISABLED_WARNING);
  }

  @Test
  void withTokenHashTheStatelessServerIsMounted() {
    runner
        .withPropertyValues(FixtureCredentials.MCP_HASH_PROPERTY)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpStatelessSyncServer.class);
              assertThat(context).hasSingleBean(RouterFunction.class);
              assertThat(context).hasBean(INVESTAI_TOOLS);
              assertThat(context).hasBean(TokenProbe.PROBE);
            });

    verify(log, never()).warn(McpEndpointActivation.DISABLED_WARNING);
  }

  @Test
  void theTokenHashOverridesAnExplicitSwitch() {
    var environment =
        new MockEnvironment()
            .withProperty(McpEndpointActivation.SERVER_ENABLED, "true")
            .withProperty(McpEndpointActivation.TOKEN_HASH, " ");

    activation.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty(McpEndpointActivation.SERVER_ENABLED, Boolean.class))
        .isFalse();
  }

  @Test
  void runsAfterTheConfigurationFiles() {
    assertThat(activation.getOrder())
        .isGreaterThan(
            org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor.ORDER);
  }

  @Test
  void theSecurityChainOfMcpFollowsTheSameCondition() {
    Conditional condition = McpSecurityConfiguration.class.getAnnotation(Conditional.class);

    assertThat(condition.value()).containsExactly(McpTokenConfigured.class);
  }

  private void activate(ConfigurableApplicationContext context) {
    activation.postProcessEnvironment(context.getEnvironment(), new SpringApplication());
  }

  /** Stands for {@code McpSecurityConfiguration}, whose filter chain needs a web security setup. */
  @Configuration(proxyBeanMethods = false)
  @Conditional(McpTokenConfigured.class)
  static class TokenProbe {

    static final String PROBE = "mcpTokenProbe";

    @Bean(PROBE)
    String mcpTokenProbe() {
      return PROBE;
    }
  }
}
