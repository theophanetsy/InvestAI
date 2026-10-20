package dev.investai.assistant.infrastructure;

import static dev.investai.assistant.AdviceFixtures.COMPUTED_AT;
import static dev.investai.assistant.AdviceFixtures.FRIDAY;
import static dev.investai.assistant.AdviceFixtures.call;
import static dev.investai.assistant.AdviceFixtures.instrument;
import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.assistant.AdviceFixtures;
import dev.investai.market.IndicatorFixtures;
import dev.investai.market.MarketTestData;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InstrumentType;
import dev.investai.support.IntegrationTest;
import dev.investai.support.McpTestClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * The market tools of the advice through {@code /mcp}, on the real database (spec 3.1, criteria 17
 * to 27 and 46): search, detail, history, screening and benchmarks.
 */
@IntegrationTest
class AdviceMarketToolsIT {

  private static final String STRUCTURED = "structuredContent";
  private static final String STATUS = "status";
  private static final String VALUE = "value";
  private static final String RESULTS = "results";
  private static final String UNTRUSTED = "untrusted";
  private static final String DATA_LIMITS = "dataLimits";
  private static final String CODE = "code";
  private static final String ELIGIBILITY = "peaEligibility";
  private static final String INDICATORS_STATUS = "indicatorsStatus";
  private static final String AS_OF = "asOf";
  private static final String FRIDAY_TEXT = "2026-10-16";
  private static final String POINTS = "points";

  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;
  @Autowired private IndicatorRepository indicators;

  @LocalServerPort private int port;

  private McpTestClient mcp;
  private InstrumentId stock;
  private InstrumentId etf;
  private InstrumentId foreign;

  @BeforeEach
  void setUp() {
    MarketTestData.clearTables(jdbc);
    mcp = new McpTestClient(port);
    stock =
        IndicatorFixtures.stock(instruments, "FR000012007", "Air Liquide (test)", COMPUTED_AT).id();
    etf =
        AdviceFixtures.listed(instruments, AdviceFixtures.CW8, InstrumentType.ETF, "Monde (test)")
            .id();
    foreign = AdviceFixtures.foreignStock(instruments, "Étrangère (test)").id();
  }

  @AfterEach
  void close() {
    mcp.close();
  }

  @Test
  void searchFindsByNameWithTheIdentifierToPassOnButNeverEchoesTheQuery() throws Exception {
    JsonNode result =
        call(mcp, SearchInstrumentsTool.NAME, "{\"query\":\"air liq\"}").get(STRUCTURED);

    assertThat(result.get("resultCount").asInt()).isEqualTo(1);
    JsonNode found = result.get(RESULTS).get(0);
    assertThat(found.get("instrumentId").asString()).isEqualTo(stock.toString());
    assertThat(found.get("type").asString()).isEqualTo("STOCK");
    assertThat(found.get(ELIGIBILITY).get(STATUS).asString()).isEqualTo("ELIGIBLE");
    assertThat(found.get(ELIGIBILITY).get("source").asString()).isEqualTo("RULE");
    assertThat(found.get(UNTRUSTED).get("name").asString()).isEqualTo("Air Liquide (test)");
    assertThat(result.get(DATA_LIMITS).values())
        .extracting(limit -> limit.get(CODE).asString())
        .containsExactly("PEA_ELIGIBILITY_INDICATIVE");
    assertThat(result.toString()).doesNotContain("air liq");
  }

  @Test
  void instrumentGivesItsCloseAndEveryIndicatorWithStatusAndDatesWithoutTrackingIt()
      throws Exception {
    prices.save(List.of(IndicatorFixtures.close(stock, FRIDAY, "170.5")), COMPUTED_AT);
    indicators.saveAll(Map.of(stock, IndicatorFixtures.sampleSet(FRIDAY)), COMPUTED_AT);

    JsonNode result = call(mcp, InstrumentTool.NAME, instrument(stock)).get(STRUCTURED);

    assertThat(result.get(INDICATORS_STATUS).asString()).isEqualTo("COMPUTED");
    assertThat(result.get(AS_OF).asString()).isEqualTo(FRIDAY_TEXT);
    assertThat(result.get("computedAt").asString()).isEqualTo("2026-10-17T07:00:00Z");
    assertThat(result.get("closesOneYear").asInt()).isEqualTo(180);
    assertThat(result.get("lastClose").get("price").asString()).isEqualTo("170.5000");
    assertThat(result.get("lastClose").get("date").asString()).isEqualTo(FRIDAY_TEXT);
    JsonNode figures = result.get("indicators");
    // Value exactly when available, never a zero in its place (sample set of IndicatorFixtures).
    assertThat(figures.get("performancePercent").get("oneMonth").get(VALUE).asString())
        .isEqualTo("2.35");
    assertThat(figures.get("performancePercent").get("oneYear").get(STATUS).asString())
        .isEqualTo("INSUFFICIENT_HISTORY");
    assertThat(figures.get("performancePercent").get("oneYear").has(VALUE)).isFalse();
    assertThat(figures.get("cac40").get("correlation").get(STATUS).asString())
        .isEqualTo("NOT_COMPUTABLE");
    assertThat(figures.get("msciWorld").get("beta").get(VALUE).asString()).isEqualTo("1.05");
    assertThat(figures.get("averageDailyTradedAmount").get(VALUE).asString())
        .isEqualTo("1234567.89");
    assertThat(figures.get("averageDailyTradedAmount").get("currency").asString()).isEqualTo("EUR");
    assertThat(result.get(DATA_LIMITS).size()).isEqualTo(6);
    assertThat(instruments.findById(stock).orElseThrow().tracked()).isFalse();
  }

  @Test
  void instrumentBeforeTheComputationHasNeitherDatesNorIndicators() throws Exception {
    JsonNode result = call(mcp, InstrumentTool.NAME, instrument(etf)).get(STRUCTURED);

    assertThat(result.get(INDICATORS_STATUS).asString()).isEqualTo("NOT_COMPUTED");
    assertThat(result.has(AS_OF)).isFalse();
    assertThat(result.has("computedAt")).isFalse();
    assertThat(result.has("indicators")).isFalse();
    assertThat(result.has("lastClose")).isFalse();
  }

  @Test
  void unknownInstrumentIsNotFoundWithoutItsIdentifier() throws Exception {
    String unknown = InstrumentId.newId().toString();

    JsonNode result = call(mcp, InstrumentTool.NAME, "{\"instrumentId\":\"" + unknown + "\"}");

    assertThat(result.get("isError").asBoolean()).isTrue();
    JsonNode error = McpTestClient.json(result.get("content").get(0).get("text").asString());
    assertThat(error.get(CODE).asString()).isEqualTo("instrument.not-found");
    assertThat(error.has("requestId")).isTrue();
    assertThat(result.toString()).doesNotContain(unknown);
  }

  @Test
  void historyGivesTheRawClosesOfThePeriodAndItsGaps() throws Exception {
    var year = AdviceFixtures.year();
    // Closes on the last 10 sessions only; three months = 63 sessions, so 64 with the reference.
    IndicatorFixtures.saveYear(prices, stock, year, i -> "1" + i % 10, 243, COMPUTED_AT);

    JsonNode result = call(mcp, PriceHistoryTool.NAME, instrument(stock)).get(STRUCTURED);

    assertThat(result.get("to").asString()).isEqualTo(FRIDAY_TEXT);
    assertThat(result.get("from").asString()).isEqualTo(year.sessions().get(252 - 63).toString());
    assertThat(result.get("sessionCount").asInt()).isEqualTo(64);
    assertThat(result.get("missingSessions").asInt()).isEqualTo(54);
    assertThat(result.get(POINTS).size()).isEqualTo(10);
    assertThat(result.get(POINTS).get(9).get("date").asString()).isEqualTo(FRIDAY_TEXT);
    assertThat(result.get(POINTS).get(9).get("close").asString()).isEqualTo("12.0000");
    assertThat(result.get(DATA_LIMITS).values())
        .extracting(limit -> limit.get(CODE).asString())
        .containsExactly("RAW_CLOSES", "OUTLIER_QUOTES");
  }

  @Test
  void historyOfAYearHasAtMost253Points() throws Exception {
    IndicatorFixtures.saveYear(prices, stock, AdviceFixtures.year(), i -> "10", 0, COMPUTED_AT);

    JsonNode result =
        call(
                mcp,
                PriceHistoryTool.NAME,
                "{\"instrumentId\":\"" + stock + "\",\"period\":\"ONE_YEAR\"}")
            .get(STRUCTURED);

    assertThat(result.get(POINTS).size()).isEqualTo(253);
    assertThat(result.get("missingSessions").asInt()).isZero();
  }

  @Test
  void screeningKeepsTheRequestedTypeAndEligibilityAndCountsTheMissingIndicators()
      throws Exception {
    LocalDate asOf = FRIDAY;
    indicators.saveAll(
        Map.of(
            stock, IndicatorFixtures.sampleSet(asOf),
            etf, IndicatorFixtures.sampleSet(asOf),
            foreign, IndicatorFixtures.sampleSet(asOf)),
        COMPUTED_AT);

    JsonNode stocks =
        call(
                mcp,
                ScreenInstrumentsTool.NAME,
                "{\"sort\":\"VOLATILITY\",\"instrumentType\":\"STOCK\"}")
            .get(STRUCTURED);
    JsonNode everything =
        call(
                mcp,
                ScreenInstrumentsTool.NAME,
                "{\"sort\":\"VOLATILITY\",\"peaEligibility\":\"ANY\",\"minPerformancePercent\":\"0\"}")
            .get(STRUCTURED);

    // The foreign stock is not eligible: left out by the default ELIGIBLE_OR_UNKNOWN.
    assertThat(stocks.get("matchCount").asInt()).isEqualTo(1);
    assertThat(stocks.get(RESULTS).get(0).get("instrumentId").asString())
        .isEqualTo(stock.toString());
    assertThat(stocks.get("criteria").get(ELIGIBILITY).asString()).isEqualTo("ELIGIBLE_OR_UNKNOWN");
    assertThat(stocks.get("criteria").get("limit").asInt()).isEqualTo(20);
    assertThat(stocks.get(AS_OF).asString()).isEqualTo(FRIDAY_TEXT);
    assertThat(stocks.has("unavailable")).isFalse();
    // One-year performance is insufficient in the sample set: every instrument is left out.
    assertThat(everything.get("matchCount").asInt()).isZero();
    assertThat(everything.get("unavailable").get("performance").asInt()).isEqualTo(3);
    assertThat(everything.get("unavailable").has("volatility")).isFalse();
  }

  @Test
  void screeningBeforeTheFirstComputationReturnsNothing() throws Exception {
    JsonNode result =
        call(mcp, ScreenInstrumentsTool.NAME, "{\"sort\":\"PERFORMANCE\"}").get(STRUCTURED);

    assertThat(result.get(INDICATORS_STATUS).asString()).isEqualTo("NOT_COMPUTED");
    assertThat(result.get(RESULTS).size()).isZero();
    assertThat(result.has(AS_OF)).isFalse();
  }

  @Test
  void benchmarksGiveTheConfiguredEtfsWithTheirRole() throws Exception {
    var cac =
        AdviceFixtures.listed(instruments, AdviceFixtures.CAC, InstrumentType.ETF, "CAC (test)");
    indicators.saveAll(Map.of(etf, IndicatorFixtures.sampleSet(FRIDAY)), COMPUTED_AT);

    JsonNode result = call(mcp, BenchmarksTool.NAME, "{}").get(STRUCTURED);

    // Configuration order: CAC, CW8, WPEA (absent from the reference data here).
    JsonNode benchmarks = result.get("benchmarks");
    assertThat(benchmarks.size()).isEqualTo(2);
    assertThat(benchmarks.get(0).get("instrumentId").asString()).isEqualTo(cac.id().toString());
    assertThat(benchmarks.get(0).get("role").asString()).isEqualTo("CAC40_REFERENCE");
    assertThat(benchmarks.get(0).get(INDICATORS_STATUS).asString()).isEqualTo("NOT_COMPUTED");
    assertThat(benchmarks.get(1).get("role").asString()).isEqualTo("MSCI_WORLD_REFERENCE");
    assertThat(benchmarks.get(1).get(ELIGIBILITY).get(STATUS).asString()).isEqualTo("ELIGIBLE");
    assertThat(benchmarks.get(1).get("indicators").get("volatilityPercent").get(VALUE).asString())
        .isEqualTo("18.40");
    assertThat(benchmarks.get(1).has(DATA_LIMITS)).isFalse();
    assertThat(result.get(AS_OF).asString()).isEqualTo(FRIDAY_TEXT);
    assertThat(result.get(DATA_LIMITS).size()).isEqualTo(6);
  }
}
