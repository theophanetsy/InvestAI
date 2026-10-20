package dev.investai.assistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.schema.jackson3.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The checks of the arguments on the server and the input schema declared to the client agree (spec
 * 3.1, criteria 9 and 41): every case is judged both by the tool and by the JSON schema validator
 * of the MCP SDK on the declared schema. The few rules a JSON schema cannot express (non-blank
 * query, positive weight, one line per instrument, total of the weights) are checked by the server
 * only: the schema stays a hint.
 */
class ToolArgumentsTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String INSTRUMENT_ID = "{\"instrumentId\":\"";
  private static final String END_OF_TEXT = "\"}";
  private static final String UUID = "00000000-0000-4000-8000-000000000001";
  private static final String SEARCH = SearchInstrumentsTool.NAME;
  private static final String INSTRUMENT = InstrumentTool.NAME;
  private static final String HISTORY = PriceHistoryTool.NAME;
  private static final String SCREEN = ScreenInstrumentsTool.NAME;
  private static final String ALLOCATION = AllocationTool.NAME;
  private static final String VALID = "valid";
  private static final String INVALID = "invalid";
  private static final String SERVER_ONLY = "server only";

  private final MockedTools tools = new MockedTools();
  private final DefaultJsonSchemaValidator schemas = new DefaultJsonSchemaValidator();

  static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of(SEARCH, "{\"query\":\"Air\"}", VALID),
        Arguments.of(SEARCH, "{\"query\":\"" + "x".repeat(100) + END_OF_TEXT, VALID),
        Arguments.of(SEARCH, "{\"query\":\"" + "x".repeat(101) + END_OF_TEXT, INVALID),
        Arguments.of(SEARCH, "{\"query\":\"" + END_OF_TEXT, INVALID),
        Arguments.of(SEARCH, "{\"query\":\"   \"}", SERVER_ONLY),
        Arguments.of(SEARCH, "{\"query\":\"a" + escaped("001b") + END_OF_TEXT, INVALID),
        Arguments.of(SEARCH, "{\"query\":\"a" + escaped("200b") + END_OF_TEXT, INVALID),
        Arguments.of(SEARCH, "{\"query\":\"a" + escaped("2028") + END_OF_TEXT, INVALID),
        Arguments.of(SEARCH, "{\"query\":1}", INVALID),
        Arguments.of(SEARCH, "{}", INVALID),
        Arguments.of(SEARCH, "{\"query\":\"a\",\"other\":1}", INVALID),
        Arguments.of(INSTRUMENT, INSTRUMENT_ID + UUID + END_OF_TEXT, VALID),
        Arguments.of(INSTRUMENT, INSTRUMENT_ID + UUID + "0\"}", INVALID),
        Arguments.of(INSTRUMENT, "{\"instrumentId\":\"not-a-uuid\"}", INVALID),
        Arguments.of(INSTRUMENT, "{\"instrumentId\":42}", INVALID),
        Arguments.of(INSTRUMENT, "{}", INVALID),
        Arguments.of(HISTORY, INSTRUMENT_ID + UUID + "\",\"period\":\"ONE_YEAR\"}", VALID),
        Arguments.of(HISTORY, INSTRUMENT_ID + UUID + "\",\"period\":\"one_year\"}", INVALID),
        Arguments.of(SCREEN, "{\"sort\":\"PERFORMANCE\"}", VALID),
        Arguments.of(SCREEN, "{}", INVALID),
        Arguments.of(SCREEN, screen("\"limit\":1"), VALID),
        Arguments.of(SCREEN, screen("\"limit\":50"), VALID),
        Arguments.of(SCREEN, screen("\"limit\":0"), INVALID),
        Arguments.of(SCREEN, screen("\"limit\":51"), INVALID),
        Arguments.of(SCREEN, screen("\"limit\":1.5"), INVALID),
        Arguments.of(SCREEN, screen("\"limit\":\"20\""), INVALID),
        Arguments.of(SCREEN, screen("\"maxVolatilityPercent\":\"999.99\""), VALID),
        Arguments.of(SCREEN, screen("\"maxVolatilityPercent\":\"1000\""), INVALID),
        Arguments.of(SCREEN, screen("\"maxVolatilityPercent\":\"1.234\""), INVALID),
        Arguments.of(SCREEN, screen("\"maxVolatilityPercent\":25"), INVALID),
        Arguments.of(SCREEN, screen("\"maxDrawdownLimitPercent\":\"20\""), VALID),
        Arguments.of(SCREEN, screen("\"maxDrawdownLimitPercent\":\"-20\""), INVALID),
        Arguments.of(SCREEN, screen("\"minPerformancePercent\":\"-5.25\""), VALID),
        Arguments.of(SCREEN, screen("\"minPerformancePercent\":\"--1\""), INVALID),
        Arguments.of(SCREEN, screen("\"minAverageDailyTradedAmountEur\":\"100000.5\""), VALID),
        Arguments.of(SCREEN, screen("\"minAverageDailyTradedAmountEur\":\"1e9\""), INVALID),
        Arguments.of(
            SCREEN, screen("\"instrumentType\":\"ETF\",\"peaEligibility\":\"ANY\""), VALID),
        Arguments.of(SCREEN, screen("\"instrumentType\":\"FUND\""), INVALID),
        Arguments.of(SCREEN, screen("\"performanceWindow\":\"TEN_YEARS\""), INVALID),
        Arguments.of(ALLOCATION, lines(1, "100"), VALID),
        Arguments.of(ALLOCATION, lines(30, "3.33"), VALID),
        Arguments.of(ALLOCATION, lines(31, "1"), INVALID),
        Arguments.of(ALLOCATION, "{\"lines\":[]}", INVALID),
        Arguments.of(ALLOCATION, "{\"lines\":[42]}", INVALID),
        Arguments.of(ALLOCATION, lines(1, "1.234"), INVALID),
        Arguments.of(ALLOCATION, lines(1, "0"), SERVER_ONLY),
        Arguments.of(ALLOCATION, lines(2, "60"), SERVER_ONLY),
        Arguments.of(ALLOCATION, duplicated(), SERVER_ONLY),
        Arguments.of(
            ALLOCATION,
            "{\"lines\":[{\"instrumentId\":\"" + UUID + "\",\"weightPercent\":\"1\",\"x\":1}]}",
            INVALID),
        Arguments.of(InvestorProfileTool.NAME, "{}", VALID),
        Arguments.of(PortfolioTool.NAME, "{\"x\":1}", INVALID),
        Arguments.of(BenchmarksTool.NAME, "{\"x\":null}", INVALID));
  }

  @ParameterizedTest
  @MethodSource("cases")
  void serverAndDeclaredSchemaAgree(String tool, String arguments, String verdict) {
    var specification = tools.tool(tool);
    Map<String, Object> values = JSON.readValue(arguments, new TypeReference<>() {});

    boolean schemaAccepts = schemas.validate(specification.tool().inputSchema(), values).valid();
    CallToolResult result =
        specification
            .callHandler()
            .apply(
                McpTransportContext.EMPTY, CallToolRequest.builder(tool).arguments(values).build());
    boolean serverAccepts = !isInvalidArgument(result);

    assertThat(serverAccepts).as("server").isEqualTo(VALID.equals(verdict));
    assertThat(schemaAccepts).as("declared schema").isEqualTo(!INVALID.equals(verdict));
  }

  private static boolean isInvalidArgument(CallToolResult result) {
    return Boolean.TRUE.equals(result.isError())
        && ((TextContent) result.content().getFirst())
            .text()
            .contains("\"code\":\"" + ToolCallRejected.INVALID_ARGUMENT + "\"");
  }

  private static String screen(String criterion) {
    return "{\"sort\":\"VOLATILITY\"," + criterion + "}";
  }

  private static String lines(int count, String weight) {
    return IntStream.range(0, count)
        .mapToObj(
            i ->
                "{\"instrumentId\":\"00000000-0000-4000-8000-%012d\",\"weightPercent\":\"%s\"}"
                    .formatted(i, weight))
        .collect(Collectors.joining(",", "{\"lines\":[", "]}"));
  }

  private static String duplicated() {
    String line = INSTRUMENT_ID + UUID + "\",\"weightPercent\":\"1\"}";
    return "{\"lines\":[" + line + "," + line + "]}";
  }

  /** A JSON escape sequence, built so that no tool turns it into the character itself. */
  private static String escaped(String hex) {
    return (char) 0x5C + "u" + hex;
  }
}
