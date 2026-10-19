package dev.investai.portfolio.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.investai.market.MarketTestData;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.PeaEligibility;
import dev.investai.market.domain.PeaEligibilityStatus;
import dev.investai.portfolio.application.PortfolioCommands;
import dev.investai.portfolio.application.PortfolioOverview;
import dev.investai.portfolio.application.PortfolioQueries;
import dev.investai.portfolio.domain.Cash;
import dev.investai.portfolio.domain.PortfolioLine;
import dev.investai.portfolio.domain.PortfolioLineId;
import dev.investai.portfolio.domain.PricedLine;
import dev.investai.portfolio.domain.Quote;
import dev.investai.portfolio.domain.Valuation;
import dev.investai.support.SecuredWebMvcTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Valuation and cash API (spec 2.3, criteria 4, 6 and 9). */
@SecuredWebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

  private static final String PORTFOLIO = "/api/v1/portfolio";
  private static final String USER = "someone";
  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

  @Autowired private MockMvcTester mvc;
  @MockitoBean private PortfolioQueries queries;
  @MockitoBean private PortfolioCommands commands;

  private final Instrument airLiquide =
      new Instrument(
          new InstrumentId(UUID.fromString("7d0f0a52-3c3e-4f4e-9d4e-0b9c3f1a2b3c")),
          new Listing(new Isin("FR0000120073"), Mic.XPAR),
          "AI",
          "Air Liquide",
          InstrumentType.STOCK,
          MarketTestData.EUR,
          NOW,
          false,
          PeaEligibility.byRule(PeaEligibilityStatus.ELIGIBLE));

  @Test
  void portfolioRequiresASession() {
    assertThat(mvc.get().uri(PORTFOLIO).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void valuationAmountsAreDecimalTexts() {
    var line =
        new PortfolioLine(
            PortfolioLineId.newId(), airLiquide.id(), 10, new BigDecimal("150.1234"), NOW);
    var valuation =
        Valuation.of(
            List.of(
                new PricedLine(
                    line, new Quote(new BigDecimal("173.1600"), LocalDate.parse("2026-10-16")))),
            new Cash(new BigDecimal("268.40"), NOW));
    given(queries.overview())
        .willReturn(new PortfolioOverview(valuation, Map.of(airLiquide.id(), airLiquide)));

    var response = mvc.get().uri(PORTFOLIO).with(user(USER)).exchange();

    assertThat(response).hasStatusOk();
    var json = assertThat(response).bodyJson();
    json.extractingPath("$.total").isEqualTo("2000.00");
    json.extractingPath("$.currency").isEqualTo("EUR");
    json.extractingPath("$.complete").isEqualTo(true);
    json.extractingPath("$.unpricedLines").isEqualTo(0);
    json.extractingPath("$.cash.weightPercent").isEqualTo("13.42");
    json.extractingPath("$.lines[0].id").isEqualTo(line.id().toString());
    json.extractingPath("$.lines[0].instrument.ticker").isEqualTo("AI");
    json.extractingPath("$.lines[0].instrument.peaEligibility.source").isEqualTo("RULE");
    json.extractingPath("$.lines[0].quantity").isEqualTo(10);
    json.extractingPath("$.lines[0].averagePrice").isEqualTo("150.1234");
    json.extractingPath("$.lines[0].lastClose.close").isEqualTo("173.1600");
    json.extractingPath("$.lines[0].lastClose.date").isEqualTo("2026-10-16");
    json.extractingPath("$.lines[0].value").isEqualTo("1731.60");
    json.extractingPath("$.lines[0].unrealizedGain").isEqualTo("230.37");
    json.extractingPath("$.lines[0].unrealizedGainPercent").isEqualTo("15.35");
    json.extractingPath("$.lines[0].weightPercent").isEqualTo("86.58");
  }

  @Test
  void cashIsDeclaredAsDecimalText() {
    given(commands.setCash(BigDecimal.ZERO)).willReturn(new Cash(BigDecimal.ZERO, NOW));

    var response = putCash("{\"amount\":\"0\"}");

    assertThat(response).hasStatusOk();
    assertThat(response).bodyJson().extractingPath("$.amount").isEqualTo("0.00");
    assertThat(response).bodyJson().doesNotHavePath("$.weightPercent");
  }

  @Test
  void cashRequiresTheCsrfToken() {
    var response =
        mvc.put()
            .uri(PORTFOLIO + "/cash")
            .with(user(USER))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":\"1\"}")
            .exchange();

    assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void invalidCashIsRejected() {
    for (String body : List.of("{\"amount\":\"-1\"}", "{\"amount\":\"1.005\"}", "{}")) {
      var response = putCash(body);

      assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
      assertThat(response).bodyJson().extractingPath("$.errors[0].field").isEqualTo("amount");
    }
  }

  private MvcTestResult putCash(String body) {
    return mvc.put()
        .uri(PORTFOLIO + "/cash")
        .with(user(USER))
        .with(csrf().asHeader())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }
}
