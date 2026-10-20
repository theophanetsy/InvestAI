package dev.investai.market.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.investai.market.IndicatorFixtures;
import dev.investai.market.MarketTestData;
import dev.investai.market.application.IndicatorQueries;
import dev.investai.market.domain.IndicatorWindow;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentIndicators;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.PeaEligibilityStatus;
import dev.investai.market.domain.Screening;
import dev.investai.market.domain.ScreeningCriteria;
import dev.investai.market.domain.ScreeningSort;
import dev.investai.market.domain.StoredIndicators;
import dev.investai.shared.domain.ResourceNotFoundException;
import dev.investai.support.SecuredWebMvcTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Indicators API (spec 2.4, criterion 9): shape of the responses and validation. */
@SecuredWebMvcTest(IndicatorController.class)
class IndicatorControllerTest {

  private static final String SCREEN = "/api/v1/market/indicators";
  private static final String USER = "someone";
  private static final String CODE = "$.code";
  private static final String FIELD = "$.errors[0].field";
  private static final String VALIDATION_FAILED = "validation.failed";
  private static final Instant COMPUTED_AT = Instant.parse("2026-10-17T07:00:00Z");

  @Autowired private MockMvcTester mvc;
  @MockitoBean private IndicatorQueries queries;

  private final Instrument airLiquide =
      new Instrument(
          new InstrumentId(UUID.fromString("7d0f0a52-3c3e-4f4e-9d4e-0b9c3f1a2b3c")),
          new Listing(new Isin("FR0000120073"), Mic.XPAR),
          "AI",
          "Air Liquide",
          InstrumentType.STOCK,
          MarketTestData.EUR,
          null,
          false,
          MarketTestData.UNKNOWN_BY_RULE);

  private final InstrumentIndicators indicators =
      new InstrumentIndicators(
          airLiquide,
          new StoredIndicators(
              airLiquide.id(),
              IndicatorFixtures.sampleSet(LocalDate.parse("2026-10-16")),
              COMPUTED_AT));

  private String indicatorsUri() {
    return "/api/v1/market/instruments/" + airLiquide.id() + "/indicators";
  }

  @Test
  void everyEndpointRequiresASession() {
    assertThat(mvc.get().uri(indicatorsUri()).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.get().uri(SCREEN).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void indicatorsAreDecimalTextsWithTheirStatus() {
    given(queries.instrument(airLiquide.id())).willReturn(indicators);

    var response = mvc.get().uri(indicatorsUri()).with(user(USER)).exchange();

    assertThat(response).hasStatusOk();
    var json = assertThat(response).bodyJson();
    json.extractingPath("$.instrument.isin").isEqualTo("FR0000120073");
    json.extractingPath("$.computedAt").isEqualTo("2026-10-17T07:00:00Z");
    json.extractingPath("$.indicators.asOf").isEqualTo("2026-10-16");
    json.extractingPath("$.indicators.closesOneYear").isEqualTo(180);
    json.extractingPath("$.indicators.performanceOneMonth.status").isEqualTo("AVAILABLE");
    json.extractingPath("$.indicators.performanceOneMonth.value").isEqualTo("2.35");
    json.extractingPath("$.indicators.performanceSixMonths.value").isEqualTo("-1.20");
    json.extractingPath("$.indicators.performanceOneYear.status").isEqualTo("INSUFFICIENT_HISTORY");
    json.doesNotHavePath("$.indicators.performanceOneYear.value");
    json.extractingPath("$.indicators.volatility.value").isEqualTo("18.40");
    json.extractingPath("$.indicators.maxDrawdown.value").isEqualTo("-12.30");
    json.extractingPath("$.indicators.msciWorld.beta.value").isEqualTo("1.05");
    json.extractingPath("$.indicators.cac40.correlation.status").isEqualTo("NOT_COMPUTABLE");
    json.extractingPath("$.indicators.averageDailyTradedAmount.value").isEqualTo("1234567.89");
    json.extractingPath("$.indicators.currency").isEqualTo("EUR");
  }

  @Test
  void indicatorsNotComputedYetAreA404WithItsCode() {
    given(queries.instrument(airLiquide.id()))
        .willThrow(new ResourceNotFoundException(IndicatorQueries.NOT_COMPUTED, "not yet"));

    var response = mvc.get().uri(indicatorsUri()).with(user(USER)).exchange();

    assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(response).bodyJson().extractingPath("$.code").isEqualTo("indicators.not-computed");
  }

  @Test
  void screeningPassesTheParsedCriteria() {
    var criteria = ArgumentCaptor.forClass(ScreeningCriteria.class);
    given(queries.screen(criteria.capture())).willReturn(new Screening(7, List.of(indicators)));

    var response =
        mvc.get()
            .uri(
                SCREEN
                    + "?peaEligibility=ELIGIBLE&maxVolatility=25&minAverageDailyTradedAmount=100000.5"
                    + "&performanceWindow=SIX_MONTHS&minPerformance=-5.25&sort=VOLATILITY&limit=10")
            .with(user(USER))
            .exchange();

    assertThat(response).hasStatusOk();
    assertThat(response).bodyJson().extractingPath("$.matchCount").isEqualTo(7);
    assertThat(response)
        .bodyJson()
        .extractingPath("$.results[0].instrument.name")
        .isEqualTo("Air Liquide");
    assertThat(criteria.getValue())
        .isEqualTo(
            new ScreeningCriteria(
                EnumSet.allOf(InstrumentType.class),
                Set.of(PeaEligibilityStatus.ELIGIBLE),
                new BigDecimal("25"),
                null,
                new BigDecimal("100000.5"),
                IndicatorWindow.SIX_MONTHS,
                new BigDecimal("-5.25"),
                ScreeningSort.VOLATILITY,
                10));
  }

  @Test
  void screeningDefaultsToEveryInstrumentByOneYearPerformanceFiftyResults() {
    var criteria = ArgumentCaptor.forClass(ScreeningCriteria.class);
    given(queries.screen(criteria.capture())).willReturn(new Screening(0, List.of()));

    assertThat(mvc.get().uri(SCREEN).with(user(USER)).exchange()).hasStatusOk();

    assertThat(criteria.getValue())
        .isEqualTo(
            new ScreeningCriteria(
                EnumSet.allOf(InstrumentType.class),
                EnumSet.allOf(PeaEligibilityStatus.class),
                null,
                null,
                null,
                IndicatorWindow.ONE_YEAR,
                null,
                ScreeningSort.PERFORMANCE,
                50));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "maxVolatility=abc",
        "maxVolatility=-1",
        "maxVolatility=1.234",
        "minAverageDailyTradedAmount=1e9",
        "minPerformance=--1",
        "limit=0",
        "limit=51"
      })
  void invalidScreeningParametersAreRejected(String parameter) {
    var response = mvc.get().uri(SCREEN + "?" + parameter).with(user(USER)).exchange();

    assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo(VALIDATION_FAILED);
    verify(queries, never()).screen(any());
  }

  @Test
  void hugeNumberIsRejectedWithoutBeingParsed() {
    var response =
        mvc.get().uri(SCREEN + "?maxVolatility=" + "9".repeat(100_000)).with(user(USER)).exchange();

    assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo(VALIDATION_FAILED);
    assertThat(response).bodyJson().extractingPath(FIELD).isEqualTo("maxVolatility");
    verify(queries, never()).screen(any());
  }

  @Test
  void unknownEnumValueIsABadRequest() {
    var response = mvc.get().uri(SCREEN + "?sort=RANDOM").with(user(USER)).exchange();

    assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo(VALIDATION_FAILED);
    assertThat(response).bodyJson().extractingPath(FIELD).isEqualTo("sort");
    verify(queries, never()).screen(any());
  }
}
