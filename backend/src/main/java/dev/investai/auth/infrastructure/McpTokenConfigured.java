package dev.investai.auth.infrastructure;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Matches when {@code INVESTAI_MCP_TOKEN_HASH} is set, like {@link McpEndpointActivation}. */
final class McpTokenConfigured implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return McpEndpointActivation.isConfigured(context.getEnvironment());
  }
}
