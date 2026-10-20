package dev.investai.architecture.fixture.application;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Deliberately uses the MCP library outside {@code infrastructure}, used only to prove that {@code
 * ArchitectureTest.ONLY_INFRASTRUCTURE_USES_MCP} fails. Not a Spring bean.
 */
public class LeakyMcpUseCase {

  /**
   * Builds an MCP result in a use case, which the rule forbids.
   *
   * @return the result
   */
  public McpSchema.CallToolResult result() {
    return McpSchema.CallToolResult.builder().build();
  }
}
