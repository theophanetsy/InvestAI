package dev.investai.assistant.infrastructure;

import static dev.investai.assistant.AdviceFixtures.COMPUTED_AT;
import static dev.investai.assistant.AdviceFixtures.FRIDAY;
import static dev.investai.assistant.AdviceFixtures.call;
import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.assistant.AdviceFixtures;
import dev.investai.market.IndicatorFixtures;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.portfolio.PortfolioTestData;
import dev.investai.portfolio.application.InvestorProfiles;
import dev.investai.portfolio.application.PortfolioCommands;
import dev.investai.portfolio.application.PortfolioQueries;
import dev.investai.portfolio.application.ProfileChange;
import dev.investai.portfolio.domain.RiskProfile;
import dev.investai.support.IntegrationTest;
import dev.investai.support.McpTestClient;
import java.math.BigDecimal;
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
 * The tools about the owner through {@code /mcp}, on the real database (spec 3.1, criteria 13 to
 * 16, 28 to 31 and 46): profile, declared PEA and computed split.
 */
@IntegrationTest
class AdviceOwnerToolsIT {

  private static final String STRUCTURED = "structuredContent";
  private static final String STATUS = "status";
  private static final String AMOUNT = "amount";
  private static final String LINES = "lines";
  private static final String VALUE = "value";
  private static final String PROFILE_MISSING = "PROFILE_MISSING";

  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;
  @Autowired private IndicatorRepository indicators;
  @Autowired private InvestorProfiles profiles;
  @Autowired private PortfolioCommands commands;
  @Autowired private PortfolioQueries portfolio;

  @LocalServerPort private int port;

  private McpTestClient mcp;
  private InstrumentId priced;
  private InstrumentId unpriced;

  @BeforeEach
  void setUp() {
    PortfolioTestData.clearTables(jdbc);
    mcp = new McpTestClient(port);
    priced = IndicatorFixtures.stock(instruments, "FR000012007", "Cotée (test)", COMPUTED_AT).id();
    unpriced =
        IndicatorFixtures.stock(instruments, "FR000013030", "Sans cours (test)", COMPUTED_AT).id();
    // A full year of closes at 100, then 125 on Friday.
    IndicatorFixtures.saveYear(
        prices, priced, AdviceFixtures.year(), i -> i == 252 ? "125" : "100", 0, COMPUTED_AT);
  }

  @AfterEach
  void close() {
    mcp.close();
  }

  @Test
  void profileIsMissingUntilFilledInThenGivenWithItsFreeTextUnderUntrusted() throws Exception {
    JsonNode missing = call(mcp, InvestorProfileTool.NAME, "{}").get(STRUCTURED);
    saveProfile();

    JsonNode profile = call(mcp, InvestorProfileTool.NAME, "{}").get(STRUCTURED);

    assertThat(missing.propertyNames()).containsExactly(STATUS);
    assertThat(missing.get(STATUS).asString()).isEqualTo(PROFILE_MISSING);
    assertThat(profile.get(STATUS).asString()).isEqualTo("OK");
    assertThat(profile.get("amountToInvest").get(AMOUNT).asString()).isEqualTo("20000.00");
    assertThat(profile.get("amountToInvest").get("currency").asString()).isEqualTo("EUR");
    assertThat(profile.get("horizonYears").asInt()).isEqualTo(10);
    assertThat(profile.get("riskProfile").get("code").asString()).isEqualTo("BALANCED");
    assertThat(profile.get("riskProfile").get("label").asString())
        .isEqualTo(RiskProfile.BALANCED.label());
    assertThat(profile.get("riskProfile").get("description").asString())
        .isEqualTo(RiskProfile.BALANCED.description());
    assertThat(profile.get("peaFiveYearsReached").asBoolean()).isTrue();
    assertThat(profile.get("untrusted").get("objectives").asString())
        .isEqualTo("Préparer la retraite");
    assertThat(profile.get("untrusted").get("exclusions").get(0).asString()).isEqualTo("tabac");
    // Least data: the opening date of the PEA never leaves.
    assertThat(profile.toString()).doesNotContain("2015-03-01", "peaOpenedOn");
  }

  @Test
  void portfolioGivesTheValuedLinesTheRiskAndNeitherPurchasePriceNorGain() throws Exception {
    commands.addLine(priced, 10, new BigDecimal("87.6543"));
    commands.addLine(unpriced, 5, null);
    commands.setCash(new BigDecimal("1000"));

    JsonNode result = call(mcp, PortfolioTool.NAME, "{}").get(STRUCTURED);

    var expected = portfolio.indicators();
    assertThat(result.get("asOf").asString()).isEqualTo(String.valueOf(expected.asOf()));
    assertThat(result.get("computedOnDemand").asBoolean()).isTrue();
    assertThat(result.has("computedAt")).isTrue();
    // 10 x 125 + 1,000 of cash = 2,250.00; the line without close is left out, never at 0.
    JsonNode valuation = result.get("valuation");
    assertThat(valuation.get("total").get(AMOUNT).asString()).isEqualTo("2250.00");
    assertThat(valuation.get("cash").get(AMOUNT).asString()).isEqualTo("1000.00");
    assertThat(valuation.get("complete").asBoolean()).isFalse();
    assertThat(valuation.get("unpricedLines").asInt()).isEqualTo(1);
    assertThat(result.get("lineCount").asInt()).isEqualTo(2);
    JsonNode first = result.get(LINES).get(0);
    assertThat(first.get("instrumentId").asString()).isEqualTo(priced.toString());
    assertThat(first.get(VALUE).get(AMOUNT).asString()).isEqualTo("1250.00");
    assertThat(first.get("weightPercent").asString()).isEqualTo("55.56");
    assertThat(first.get("riskInclusion").asString()).isEqualTo("INCLUDED");
    assertThat(
            first.get("indicators").get("performancePercent").get("oneYear").get(VALUE).asString())
        .isEqualTo("25.00");
    JsonNode second = result.get(LINES).get(1);
    assertThat(second.get("priced").asBoolean()).isFalse();
    assertThat(second.has(VALUE)).isFalse();
    assertThat(second.get("riskInclusion").asString()).isEqualTo("NOT_VALUED");
    assertThat(result.get("composition").get("cashPercent").get(VALUE).asString())
        .isEqualTo("44.44");
    assertThat(result.get("risk").get("basis").asString()).contains("espèces exclues");
    assertThat(result.get("dataLimits").size()).isEqualTo(5);
    assertThat(result.toString()).doesNotContain("87.6543", "averagePrice", "unrealized", "gain");
  }

  @Test
  void emptyPortfolioIsNotAnError() throws Exception {
    JsonNode result = call(mcp, PortfolioTool.NAME, "{}").get(STRUCTURED);

    assertThat(result.get("lineCount").asInt()).isZero();
    assertThat(result.get(LINES).size()).isZero();
    assertThat(result.get("mostCorrelatedPairs").size()).isZero();
    assertThat(result.get("risk").get("volatilityPercent").get(STATUS).asString())
        .isEqualTo("NOT_COMPUTABLE");
  }

  @Test
  void allocationIsComputedOnTheSumOfTheProfileAndTheLatestClose() throws Exception {
    String lines =
        "{\"lines\":[{\"instrumentId\":\""
            + priced
            + "\",\"weightPercent\":\"40\"},"
            + "{\"instrumentId\":\""
            + unpriced
            + "\",\"weightPercent\":\"10.5\"}]}";
    JsonNode missing = call(mcp, AllocationTool.NAME, lines).get(STRUCTURED);
    saveProfile();
    indicators.saveAll(Map.of(priced, IndicatorFixtures.sampleSet(FRIDAY)), COMPUTED_AT);

    JsonNode result = call(mcp, AllocationTool.NAME, lines).get(STRUCTURED);

    assertThat(missing.propertyNames()).containsExactly(STATUS);
    assertThat(missing.get(STATUS).asString()).isEqualTo(PROFILE_MISSING);
    // 40 % of 20,000.00 = 8,000.00 at 125: 64 shares, 8,000.00, 40.00 %.
    // 10.5 % = 2,100.00 without close: 0 share. Remaining 20,000.00 - 8,000.00 = 12,000.00.
    assertThat(result.get("asOf").asString()).isEqualTo("2026-10-16");
    JsonNode first = result.get(LINES).get(0);
    assertThat(first.get("targetAmount").get(AMOUNT).asString()).isEqualTo("8000.00");
    assertThat(first.get("quantity").asLong()).isEqualTo(64);
    assertThat(first.get("cost").get(AMOUNT).asString()).isEqualTo("8000.00");
    assertThat(first.get("actualWeightPercent").asString()).isEqualTo("40.00");
    assertThat(first.get("warnings").size()).isZero();
    JsonNode second = result.get(LINES).get(1);
    assertThat(second.get("quantity").asLong()).isZero();
    assertThat(second.get("warnings").get(0).asString()).isEqualTo("NO_EUR_CLOSE");
    assertThat(result.get("investedTotal").get(AMOUNT).asString()).isEqualTo("8000.00");
    assertThat(result.get("remainingCash").get(AMOUNT).asString()).isEqualTo("12000.00");
    assertThat(result.get("dataLimits").values())
        .extracting(limit -> limit.get("code").asString())
        .containsExactly("PEA_ELIGIBILITY_INDICATIVE", "CLOSE_IS_NOT_ORDER_PRICE");
  }

  @Test
  void allocationOfAnUnknownInstrumentIsNotFound() throws Exception {
    saveProfile();

    JsonNode result =
        call(
            mcp,
            AllocationTool.NAME,
            "{\"lines\":[{\"instrumentId\":\""
                + InstrumentId.newId()
                + "\",\"weightPercent\":\"1\"}]}");

    assertThat(result.get("isError").asBoolean()).isTrue();
    assertThat(result.get("content").get(0).get("text").asString())
        .contains("\"code\":\"instrument.not-found\"");
  }

  private void saveProfile() {
    profiles.save(
        new ProfileChange(
            new BigDecimal("20000.00"),
            10,
            RiskProfile.BALANCED,
            List.of("tabac"),
            "Préparer la retraite",
            LocalDate.parse("2015-03-01")));
  }
}
