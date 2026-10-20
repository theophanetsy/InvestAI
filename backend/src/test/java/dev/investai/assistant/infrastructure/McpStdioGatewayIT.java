package dev.investai.assistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.support.FixtureCredentials;
import dev.investai.support.IntegrationTest;
import dev.investai.support.McpTestClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;

/**
 * Relay of the Claude Desktop gateway ({@code scripts/mcp-stdio-gateway.mjs}, spec 3.0, criteria 14
 * and 15) against the real backend: JSON-RPC lines on its standard input, answers on its standard
 * output. Needs {@code node} on the {@code PATH}, like the rest of the build (frontend checks).
 */
@IntegrationTest
class McpStdioGatewayIT {

  private static final Path GATEWAY = Path.of("..", "scripts", "mcp-stdio-gateway.mjs");

  @LocalServerPort private int port;

  @Test
  void relaysInitializeListAndCallLineByLine() throws Exception {
    List<String> requests =
        List.of(
            """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":\
            "2025-06-18","capabilities":{},"clientInfo":{"name":"gateway-it","version":"1"}}}\
            """,
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}",
            """
            {"jsonrpc":"2.0","id":3,"method":"tools/call",\
            "params":{"name":"getHealth","arguments":{}}}\
            """);

    List<JsonNode> replies = run(requests);

    // One answer per request, none for the notification, in order.
    assertThat(replies).extracting(reply -> reply.get("id").asInt()).containsExactly(1, 2, 3);
    assertThat(replies.get(0).get("result").get("serverInfo").get("name").asString())
        .isEqualTo("investai");
    assertThat(replies.get(1).get("result").get("tools").get(0).get("name").asString())
        .isEqualTo(HealthTool.NAME);
    assertThat(replies.get(2).get("result").get("structuredContent").has("status")).isTrue();
  }

  private List<JsonNode> run(List<String> requests) throws IOException, InterruptedException {
    var builder =
        new ProcessBuilder("node", GATEWAY.toString())
            .redirectError(ProcessBuilder.Redirect.DISCARD);
    builder.environment().put("INVESTAI_MCP_URL", "http://127.0.0.1:" + port + "/mcp");
    builder.environment().put("INVESTAI_MCP_TOKEN", FixtureCredentials.MCP_BEARER);
    Process gateway = builder.start();
    try (OutputStream stdin = gateway.getOutputStream()) {
      stdin.write((String.join("\n", requests) + "\n").getBytes(StandardCharsets.UTF_8));
    }
    List<String> lines;
    try (var stdout =
        new BufferedReader(
            new InputStreamReader(gateway.getInputStream(), StandardCharsets.UTF_8))) {
      lines = stdout.lines().toList();
    }
    assertThat(gateway.waitFor(30, TimeUnit.SECONDS)).isTrue();
    assertThat(gateway.exitValue()).isZero();
    return lines.stream().map(McpTestClient::json).toList();
  }
}
