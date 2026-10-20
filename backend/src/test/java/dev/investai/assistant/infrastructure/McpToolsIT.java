package dev.investai.assistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.IndicatorFixtures;
import dev.investai.market.MarketTestData;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.support.FixtureCredentials;
import dev.investai.support.IntegrationTest;
import dev.investai.support.McpTestClient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * The MCP protocol on {@code /mcp} with the real server (spec 3.0, criteria 4 to 6, 11 to 13):
 * initialisation, frozen list of tools, the health tool on the real database, call logs.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class McpToolsIT {

  private static final String INITIALIZE_PARAMS =
      """
      {"protocolVersion":"2025-06-18","capabilities":{},\
      "clientInfo":{"name":"McpToolsIT","version":"1"}}\
      """;
  private static final String CALL_HEALTH = "{\"name\":\"getHealth\",\"arguments\":{}}";
  private static final String TOOLS_CALL = "tools/call";
  private static final String STATUS = "status";
  private static final String VERSION = "version";
  private static final String CLOSES_AS_OF = "closesAsOf";
  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final Instant COMPUTED_AT = Instant.parse("2026-10-19T08:00:00Z");

  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;
  @Autowired private IndicatorRepository indicators;

  @Value("${investai.version}")
  private String version;

  @LocalServerPort private int port;

  private McpTestClient mcp;

  @BeforeEach
  void setUp() {
    MarketTestData.clearTables(jdbc);
    mcp = new McpTestClient(port);
  }

  @AfterEach
  void close() {
    mcp.close();
  }

  @Test
  void initializeAnnouncesInvestaiWithToolsOnly() throws Exception {
    JsonNode result = mcp.result("initialize", INITIALIZE_PARAMS);

    assertThat(result.get("serverInfo").get("name").asString()).isEqualTo("investai");
    assertThat(result.get("serverInfo").get(VERSION).asString()).isEqualTo(version);
    assertThat(result.get("capabilities").propertyNames()).containsExactly("tools");
  }

  @Test
  void toolListIsFrozen() throws Exception {
    JsonNode tools = mcp.result("tools/list", "{}").get("tools");

    assertThat(tools.size()).isEqualTo(1);
    // Every tool declares its output: the SDK would otherwise log a structured result in full.
    assertThat(tools.values()).allSatisfy(tool -> assertThat(tool.has("outputSchema")).isTrue());
    JsonNode health = tools.get(0);
    assertThat(health.get("name").asString()).isEqualTo(HealthTool.NAME);
    assertThat(health.get("description").asString()).contains(CLOSES_AS_OF, "Lecture seule");
    JsonNode annotations = health.get("annotations");
    assertThat(annotations.get("readOnlyHint").asBoolean()).isTrue();
    assertThat(annotations.get("destructiveHint").asBoolean()).isFalse();
    assertThat(annotations.get("idempotentHint").asBoolean()).isTrue();
    assertThat(annotations.get("openWorldHint").asBoolean()).isFalse();
    assertThat(health.get("inputSchema").get("type").asString()).isEqualTo("object");
    assertThat(health.get("inputSchema").path("properties").size()).isZero();
    assertThat(health.get("outputSchema").get("properties").propertyNames())
        .containsExactlyInAnyOrder(
            STATUS, VERSION, CLOSES_AS_OF, "indicatorsAsOf", "indicatorsComputedAt");
    assertThat(health.get("outputSchema").path("required").values())
        .map(JsonNode::asString)
        .containsExactlyInAnyOrder(STATUS, VERSION);
  }

  @Test
  void healthReportsTheDatesOfTheData() throws Exception {
    var stock = IndicatorFixtures.stock(instruments, "FR000012007", "Santé (test)", COMPUTED_AT);
    prices.save(List.of(IndicatorFixtures.close(stock.id(), FRIDAY, "100")), COMPUTED_AT);
    indicators.saveAll(Map.of(stock.id(), IndicatorFixtures.sampleSet(FRIDAY)), COMPUTED_AT);

    JsonNode result = mcp.result(TOOLS_CALL, CALL_HEALTH);

    assertThat(result.path("isError").asBoolean()).isFalse();
    JsonNode health = result.get("structuredContent");
    assertThat(health.get(STATUS).asString()).isEqualTo("READY");
    assertThat(health.get(VERSION).asString()).isEqualTo(version);
    assertThat(health.get(CLOSES_AS_OF).asString()).isEqualTo("2026-10-16");
    assertThat(health.get("indicatorsAsOf").asString()).isEqualTo("2026-10-16");
    assertThat(health.get("indicatorsComputedAt").asString()).isEqualTo("2026-10-19T08:00:00Z");
    // Same JSON as text, for clients that ignore structured content.
    JsonNode text = result.get("content").get(0);
    assertThat(text.get("type").asString()).isEqualTo("text");
    assertThat(McpTestClient.json(text.get("text").asString())).isEqualTo(health);
  }

  @Test
  void healthOfAnEmptyDatabaseOmitsTheDates() throws Exception {
    JsonNode health = mcp.result(TOOLS_CALL, CALL_HEALTH).get("structuredContent");

    assertThat(health.get(STATUS).asString()).isEqualTo("NO_CLOSES");
    assertThat(health.propertyNames()).containsExactlyInAnyOrder(STATUS, VERSION);
  }

  @Test
  void eachToolCallIsLoggedOnceWithItsRequestIdButNeverItsResult(CapturedOutput output)
      throws Exception {
    var response = mcp.call(TOOLS_CALL, CALL_HEALTH);
    String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();

    assertThat(output.getOut().lines())
        .filteredOn(line -> line.contains("MCP tool call"))
        .filteredOn(line -> line.contains(requestId))
        .singleElement()
        .satisfies(
            line ->
                assertThat(line)
                    .contains("tool=getHealth", "status=ok", "durationMs=", "resultBytes=")
                    .doesNotContain("NO_CLOSES"));
    assertThat(output.getAll()).doesNotContain(FixtureCredentials.MCP_BEARER);
  }

  @Test
  void unknownToolIsAProtocolErrorWithoutCallingAnything(CapturedOutput output) throws Exception {
    var response = mcp.call(TOOLS_CALL, "{\"name\":\"runSql\",\"arguments\":{}}");
    String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();

    assertThat(response.statusCode()).isEqualTo(200);
    // JSON-RPC "invalid params" from the SDK: the tool is not in the allow-list.
    assertThat(McpTestClient.json(response.body()).get("error").get("code").asInt())
        .isEqualTo(-32_602);
    assertThat(output.getOut().lines())
        .filteredOn(line -> line.contains("MCP tool call") && line.contains(requestId))
        .isEmpty();
  }

  @Test
  void healthBeforeTheFirstComputationOmitsTheIndicatorDates() throws Exception {
    var stock = IndicatorFixtures.stock(instruments, "FR000012007", "Santé (test)", COMPUTED_AT);
    prices.save(List.of(IndicatorFixtures.close(stock.id(), FRIDAY, "100")), COMPUTED_AT);

    JsonNode health = mcp.result(TOOLS_CALL, CALL_HEALTH).get("structuredContent");

    assertThat(health.get(STATUS).asString()).isEqualTo("INDICATORS_NOT_COMPUTED");
    assertThat(health.propertyNames()).containsExactlyInAnyOrder(STATUS, VERSION, CLOSES_AS_OF);
  }
}
