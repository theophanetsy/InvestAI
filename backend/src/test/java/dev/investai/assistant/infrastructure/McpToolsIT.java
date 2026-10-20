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
import java.nio.charset.StandardCharsets;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The MCP protocol on {@code /mcp} with the real server (spec 3.0, criteria 4 to 6, 11 to 13; spec
 * 3.1, criteria 11, 32 and 42): initialisation with the instructions, frozen lists of tools and
 * prompts, the health tool on the real database, call logs.
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
  private static final String DESCRIPTION = "description";
  private static final String NAME = "name";
  private static final String PROPERTIES = "properties";
  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final Instant COMPUTED_AT = Instant.parse("2026-10-19T08:00:00Z");

  /** The nine tools of spec 3.1, criterion 11, in the order of {@code tools/list}. */
  private static final List<String> TOOLS =
      List.of(
          "getHealth",
          "getInvestorProfile",
          "getPortfolio",
          "searchInstruments",
          "getInstrument",
          "getPriceHistory",
          "screenInstruments",
          "getBenchmarks",
          "computeAllocation");

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
  void initializeAnnouncesInvestaiWithToolsPromptsAndTheInstructions() throws Exception {
    JsonNode result = mcp.result("initialize", INITIALIZE_PARAMS);

    assertThat(result.get("serverInfo").get(NAME).asString()).isEqualTo("investai");
    assertThat(result.get("serverInfo").get(VERSION).asString()).isEqualTo(version);
    assertThat(result.get("capabilities").propertyNames())
        .containsExactlyInAnyOrder("tools", "prompts");
    assertThat(result.get("instructions").asString())
        .isEqualTo(text(AdviceGuidance.SERVER_INSTRUCTIONS));
  }

  @Test
  void toolListIsFrozen() throws Exception {
    JsonNode tools = mcp.result("tools/list", "{}").get("tools");
    JsonNode frozen =
        McpTestClient.json(text("assistant/frozen-input-schemas.json")); // without descriptions

    assertThat(tools.values()).extracting(tool -> tool.get(NAME).asString()).isEqualTo(TOOLS);
    assertThat(tools.values())
        .allSatisfy(
            tool -> {
              String name = tool.get(NAME).asString();
              // Every tool declares its output: the SDK would otherwise log a structured result.
              assertThat(tool.has("outputSchema")).as(name).isTrue();
              assertThat(tool.get(DESCRIPTION).asString()).as(name).contains("Lecture seule");
              JsonNode annotations = tool.get("annotations");
              assertThat(annotations.get("readOnlyHint").asBoolean()).as(name).isTrue();
              assertThat(annotations.get("destructiveHint").asBoolean()).as(name).isFalse();
              assertThat(annotations.get("idempotentHint").asBoolean()).as(name).isTrue();
              assertThat(annotations.get("openWorldHint").asBoolean()).as(name).isFalse();
              assertThat(withoutDescriptions(tool.get("inputSchema")))
                  .as(name)
                  .isEqualTo(frozen.get(name));
              assertThat(tool.get("inputSchema").get(PROPERTIES).values())
                  .as(name)
                  .allSatisfy(property -> assertThat(property.has(DESCRIPTION)).isTrue());
            });
  }

  @Test
  void healthOutputIsUnchanged() throws Exception {
    JsonNode health = mcp.result("tools/list", "{}").get("tools").get(0);

    assertThat(health.get(DESCRIPTION).asString()).contains(CLOSES_AS_OF);
    assertThat(health.get("outputSchema").get(PROPERTIES).propertyNames())
        .containsExactlyInAnyOrder(
            STATUS, VERSION, CLOSES_AS_OF, "indicatorsAsOf", "indicatorsComputedAt");
    assertThat(health.get("outputSchema").path("required").values())
        .map(JsonNode::asString)
        .containsExactlyInAnyOrder(STATUS, VERSION);
  }

  @Test
  void promptListIsFrozenAndThePromptIsTheVersionedText(CapturedOutput output) throws Exception {
    JsonNode prompts = mcp.result("prompts/list", "{}").get("prompts");

    assertThat(prompts.size()).isEqualTo(1);
    JsonNode prompt = prompts.get(0);
    assertThat(prompt.get(NAME).asString()).isEqualTo(AdvicePrompt.NAME);
    assertThat(prompt.path("arguments").size()).isZero();
    assertThat(prompt.get(DESCRIPTION).asString()).isNotBlank();

    JsonNode got = mcp.result("prompts/get", "{\"name\":\"conseil-placement\"}");

    JsonNode message = got.get("messages").get(0);
    assertThat(message.get("role").asString()).isEqualTo("user");
    assertThat(message.get("content").get("text").asString())
        .isEqualTo(text(AdviceGuidance.ADVICE_PROMPT));
    assertThat(output.getOut())
        .contains("MCP prompt get: prompt=conseil-placement status=ok durationMs=");
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

  private static String text(String path) throws java.io.IOException {
    return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
  }

  /** The schema without the descriptions of its parameters, written in French elsewhere. */
  private static JsonNode withoutDescriptions(JsonNode schema) {
    JsonNode copy = schema.deepCopy();
    copy.path(PROPERTIES).values().forEach(McpToolsIT::removeDescriptions);
    return copy;
  }

  private static void removeDescriptions(JsonNode property) {
    ((ObjectNode) property).remove(DESCRIPTION);
    property.path("items").path(PROPERTIES).values().forEach(McpToolsIT::removeDescriptions);
  }
}
