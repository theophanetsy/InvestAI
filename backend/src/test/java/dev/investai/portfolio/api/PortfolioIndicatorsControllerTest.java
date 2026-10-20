package dev.investai.portfolio.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.investai.market.IndicatorFixtures;
import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.Instrument;
import dev.investai.portfolio.application.PortfolioIndicators;
import dev.investai.portfolio.application.PortfolioOverview;
import dev.investai.portfolio.application.PortfolioQueries;
import dev.investai.portfolio.domain.Composition;
import dev.investai.portfolio.domain.CorrelationMatrix;
import dev.investai.portfolio.domain.CurvePoint;
import dev.investai.portfolio.domain.LineExclusion;
import dev.investai.portfolio.domain.PortfolioLine;
import dev.investai.portfolio.domain.PortfolioLineId;
import dev.investai.portfolio.domain.PortfolioRisk;
import dev.investai.portfolio.domain.PricedLine;
import dev.investai.portfolio.domain.Quote;
import dev.investai.portfolio.domain.Valuation;
import dev.investai.support.SecuredWebMvcTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Portfolio indicators API (spec 2.4, criteria 7 to 9): shape of the response. */
@SecuredWebMvcTest(PortfolioIndicatorsController.class)
class PortfolioIndicatorsControllerTest {

  private static final String INDICATORS = "/api/v1/portfolio/indicators";
  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");
  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");

  @Autowired private MockMvcTester mvc;
  @MockitoBean private PortfolioQueries queries;

  private final Instrument airLiquide = IndicatorFixtures.airLiquide();

  @Test
  void indicatorsRequireASession() {
    assertThat(mvc.get().uri(INDICATORS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void indicatorsAreDecimalTextsWithStatusesExclusionsAndTheCurve() {
    var line = new PortfolioLine(PortfolioLineId.newId(), airLiquide.id(), 10, null, NOW);
    var valuation =
        Valuation.of(
            List.of(new PricedLine(line, new Quote(new BigDecimal("173.1600"), FRIDAY))), null);
    var composition =
        new Composition(
            1,
            of("100.00"),
            of("1.0000"),
            Indicator.NOT_COMPUTABLE,
            of("0.00"),
            of("100.00"),
            of("0.00"));
    var risk =
        new PortfolioRisk(
            of("18.40"),
            of("-12.30"),
            List.of(
                new CurvePoint(FRIDAY.minusDays(1), hundred(), hundred(), hundred()),
                new CurvePoint(FRIDAY, new BigDecimal("101.50"), null, new BigDecimal("99.20"))),
            new CorrelationMatrix(List.of(airLiquide.id()), List.of(List.of(of("1.00")))),
            Map.of(line.id(), LineExclusion.INSUFFICIENT_HISTORY));
    given(queries.indicators())
        .willReturn(
            new PortfolioIndicators(
                new PortfolioOverview(valuation, Map.of(airLiquide.id(), airLiquide)),
                FRIDAY,
                Map.of(airLiquide.id(), IndicatorFixtures.sampleSet(FRIDAY)),
                composition,
                risk));

    var response = mvc.get().uri(INDICATORS).with(user("someone")).exchange();

    assertThat(response).hasStatusOk();
    var json = assertThat(response).bodyJson();
    json.extractingPath("$.asOf").isEqualTo("2026-10-16");
    json.extractingPath("$.total").isEqualTo("1731.60");
    json.extractingPath("$.currency").isEqualTo("EUR");
    json.extractingPath("$.composition.lineCount").isEqualTo(1);
    json.extractingPath("$.composition.herfindahlIndex.value").isEqualTo("1.0000");
    json.extractingPath("$.composition.cashPercent.status").isEqualTo("NOT_COMPUTABLE");
    json.extractingPath("$.volatility.value").isEqualTo("18.40");
    json.extractingPath("$.maxDrawdown.value").isEqualTo("-12.30");
    json.extractingPath("$.lines[0].instrument.name").isEqualTo("Air Liquide");
    json.extractingPath("$.lines[0].weightPercent").isEqualTo("100.00");
    json.extractingPath("$.lines[0].indicators.performanceOneMonth.value").isEqualTo("2.35");
    json.extractingPath("$.lines[0].exclusion").isEqualTo("INSUFFICIENT_HISTORY");
    json.extractingPath("$.curve[1].portfolio").isEqualTo("101.50");
    json.doesNotHavePath("$.curve[1].msciWorld");
    json.extractingPath("$.correlations.instrumentIds[0]").isEqualTo(airLiquide.id().toString());
    json.extractingPath("$.correlations.rows[0][0].value").isEqualTo("1.00");
  }

  @Test
  void withoutAnyCloseTheDateAndLineIndicatorsAreAbsent() {
    var valuation = Valuation.of(List.of(), null);
    given(queries.indicators())
        .willReturn(
            new PortfolioIndicators(
                new PortfolioOverview(valuation, Map.of()),
                null,
                Map.of(),
                Composition.of(valuation, Map.of()),
                PortfolioRisk.withoutCloses(List.of())));

    var response = mvc.get().uri(INDICATORS).with(user("someone")).exchange();

    assertThat(response).hasStatusOk();
    assertThat(response).bodyJson().doesNotHavePath("$.asOf");
    assertThat(response)
        .bodyJson()
        .extractingPath("$.volatility.status")
        .isEqualTo("NOT_COMPUTABLE");
    assertThat(response).bodyJson().extractingPath("$.curve").asArray().isEmpty();
  }

  private static Indicator of(String value) {
    return Indicator.of(new BigDecimal(value));
  }

  private static BigDecimal hundred() {
    return new BigDecimal("100.00");
  }
}
