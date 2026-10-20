package dev.investai.assistant.infrastructure;

import static dev.investai.assistant.AdviceFixtures.COMPUTED_AT;
import static dev.investai.assistant.AdviceFixtures.FRIDAY;
import static dev.investai.assistant.AdviceFixtures.call;
import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.assistant.AdviceFixtures;
import dev.investai.market.IndicatorFixtures;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.portfolio.PortfolioTestData;
import dev.investai.portfolio.application.InvestorProfiles;
import dev.investai.portfolio.application.PortfolioCommands;
import dev.investai.portfolio.application.ProfileChange;
import dev.investai.portfolio.domain.InvestorProfile;
import dev.investai.portfolio.domain.PortfolioLine;
import dev.investai.portfolio.domain.RiskProfile;
import dev.investai.support.IntegrationTest;
import dev.investai.support.McpTestClient;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * Results stay bounded in the worst case (spec 3.1, criteria 8 and 46): the most elements, every
 * text at the longest the database stores, in four-byte characters.
 */
@IntegrationTest
class AdviceToolsBoundsIT {

  /** 64 KiB. */
  private static final int MAX_RESULT_BYTES = 64 * 1024;

  /** More instruments than any tool returns. */
  private static final int INSTRUMENTS = 60;

  /** A character of four bytes in UTF-8 (an emoji): the widest per code point (names). */
  private static final String WIDE = new String(Character.toChars(0x1F600));

  /** A character of three bytes in UTF-8 and one UTF-16 unit: the widest per char (profile). */
  private static final String WIDE_CHAR = String.valueOf((char) 0x8A9E);

  private static final String STRUCTURED = "structuredContent";

  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private IndicatorRepository indicators;
  @Autowired private InvestorProfiles profiles;
  @Autowired private PortfolioCommands commands;

  @LocalServerPort private int port;

  private McpTestClient mcp;
  private List<Instrument> listed;

  @BeforeEach
  void setUp() {
    PortfolioTestData.clearTables(jdbc);
    mcp = new McpTestClient(port);
    listed = AdviceFixtures.wideInstruments(instruments, INSTRUMENTS, WIDE);
    IndicatorFixtures.insertSyntheticYear(jdbc, AdviceFixtures.year(), COMPUTED_AT);
    var set = IndicatorFixtures.sampleSet(FRIDAY);
    indicators.saveAll(
        listed.stream().collect(Collectors.toMap(Instrument::id, instrument -> set)), COMPUTED_AT);
  }

  @AfterEach
  void close() {
    mcp.close();
  }

  @Test
  void portfolioOfMoreThanFiftyLinesStaysWithinTheBound() throws Exception {
    listed.forEach(
        instrument -> commands.addLine(instrument.id(), PortfolioLine.MAX_QUANTITY, null));
    commands.setCash(new BigDecimal("9999999999999.99"));

    JsonNode result = structured(PortfolioTool.NAME, "{}");

    assertThat(result.get("lineCount").asInt()).isEqualTo(INSTRUMENTS);
    assertThat(result.get("linesTruncated").asBoolean()).isTrue();
    assertThat(result.get("lines").size()).isEqualTo(PortfolioResult.MAX_LINES);
    assertThat(result.get("mostCorrelatedPairs").size()).isEqualTo(PortfolioResult.MAX_PAIRS);
    String name = result.get("lines").get(0).get("untrusted").get("name").asString();
    assertThat(name.codePointCount(0, name.length())).isEqualTo(UntrustedInstrument.MAX_NAME);
    assertWithinBound(result);
  }

  @Test
  void screeningOfFiftyResultsStaysWithinTheBound() throws Exception {
    JsonNode result =
        structured(
            ScreenInstrumentsTool.NAME,
            "{\"sort\":\"VOLATILITY\",\"limit\":50,\"maxVolatilityPercent\":\"999.99\","
                + "\"maxDrawdownLimitPercent\":\"999.99\","
                + "\"minAverageDailyTradedAmountEur\":\"0\",\"minPerformancePercent\":\"-999999.99\","
                + "\"performanceWindow\":\"ONE_MONTH\",\"peaEligibility\":\"ANY\"}");

    assertThat(result.get("results").size()).isEqualTo(50);
    assertWithinBound(result);
  }

  @Test
  void searchOfTwentyResultsStaysWithinTheBound() throws Exception {
    JsonNode result = structured(SearchInstrumentsTool.NAME, "{\"query\":\"x\"}");

    assertThat(result.get("results").size()).isEqualTo(20);
    assertWithinBound(result);
  }

  @Test
  void allocationOfThirtyLinesStaysWithinTheBound() throws Exception {
    profiles.save(
        new ProfileChange(
            new BigDecimal("9999999999999.99"), 10, RiskProfile.AGGRESSIVE, List.of(), null, null));
    String lines =
        listed.stream()
            .limit(AllocationTool.MAX_LINES)
            .map(Instrument::id)
            .map(InstrumentId::toString)
            .map(id -> "{\"instrumentId\":\"" + id + "\",\"weightPercent\":\"3.33\"}")
            .collect(Collectors.joining(",", "{\"lines\":[", "]}"));

    JsonNode result = structured(AllocationTool.NAME, lines);

    assertThat(result.get("lines").size()).isEqualTo(AllocationTool.MAX_LINES);
    assertWithinBound(result);
  }

  @Test
  void historyBenchmarksInstrumentAndProfileStayWithinTheBound() throws Exception {
    InstrumentId first = listed.getFirst().id();
    profiles.save(
        new ProfileChange(
            new BigDecimal("9999999999999.99"),
            InvestorProfile.MAX_HORIZON_YEARS,
            RiskProfile.AGGRESSIVE,
            IntStream.range(0, InvestorProfile.MAX_EXCLUSIONS)
                .mapToObj(i -> i + WIDE_CHAR.repeat(InvestorProfile.MAX_EXCLUSION_LENGTH - 2))
                .toList(),
            WIDE_CHAR.repeat(InvestorProfile.MAX_OBJECTIVES_LENGTH),
            null));

    assertWithinBound(
        structured(
            PriceHistoryTool.NAME, "{\"instrumentId\":\"" + first + "\",\"period\":\"ONE_YEAR\"}"));
    assertWithinBound(structured(InstrumentTool.NAME, AdviceFixtures.instrument(first)));
    assertWithinBound(structured(BenchmarksTool.NAME, "{}"));
    assertWithinBound(structured(InvestorProfileTool.NAME, "{}"));
  }

  private JsonNode structured(String tool, String arguments) {
    try {
      JsonNode result = call(mcp, tool, arguments);
      assertThat(result.path("isError").asBoolean()).as(tool).isFalse();
      return result.get(STRUCTURED);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void assertWithinBound(JsonNode result) {
    int bytes = result.toString().getBytes(StandardCharsets.UTF_8).length;
    assertThat(bytes).as("bytes of the structured result").isLessThanOrEqualTo(MAX_RESULT_BYTES);
  }
}
