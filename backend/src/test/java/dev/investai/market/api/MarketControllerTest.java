package dev.investai.market.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.investai.market.MarketTestData;
import dev.investai.market.application.InstrumentTracking;
import dev.investai.market.application.MarketQueries;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.InvalidPriceRangeException;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.PriceSource;
import dev.investai.shared.domain.ResourceNotFoundException;
import dev.investai.support.SecuredWebMvcTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SecuredWebMvcTest(MarketController.class)
class MarketControllerTest {

  private static final String TRACKING = "/tracking";
  private static final String FRIDAY = "2026-10-16";

  private static final String INSTRUMENTS = "/api/v1/market/instruments";
  private static final String USER = "someone";
  private static final Currency EUR = Currency.getInstance("EUR");

  @Autowired private MockMvcTester mvc;
  @MockitoBean private MarketQueries queries;
  @MockitoBean private InstrumentTracking tracking;

  private final Instrument airLiquide =
      new Instrument(
          new InstrumentId(UUID.fromString("7d0f0a52-3c3e-4f4e-9d4e-0b9c3f1a2b3c")),
          new Listing(new Isin("FR0000120073"), Mic.XPAR),
          "AI",
          "Air Liquide",
          InstrumentType.STOCK,
          EUR,
          Instant.parse("2026-10-19T08:00:00Z"),
          true,
          MarketTestData.UNKNOWN_BY_RULE);

  @Test
  void everyEndpointRequiresASession() {
    assertThat(mvc.get().uri(INSTRUMENTS + "?query=air").exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(
            mvc.put()
                .uri(INSTRUMENTS + "/" + airLiquide.id() + TRACKING)
                .with(csrf().asHeader())
                .exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void searchReturnsSummaries() {
    given(queries.search("air")).willReturn(List.of(airLiquide));

    var response = mvc.get().uri(INSTRUMENTS + "?query=air").with(user(USER)).exchange();

    assertThat(response).hasStatusOk();
    assertThat(response).bodyJson().extractingPath("$[0].id").isEqualTo(airLiquide.id().toString());
    assertThat(response).bodyJson().extractingPath("$[0].isin").isEqualTo("FR0000120073");
    assertThat(response).bodyJson().extractingPath("$[0].mic").isEqualTo("XPAR");
    assertThat(response).bodyJson().extractingPath("$[0].ticker").isEqualTo("AI");
    assertThat(response).bodyJson().extractingPath("$[0].type").isEqualTo("STOCK");
    assertThat(response).bodyJson().extractingPath("$[0].currency").isEqualTo("EUR");
    assertThat(response).bodyJson().extractingPath("$[0].tracked").isEqualTo(true);
    assertThat(response)
        .bodyJson()
        .extractingPath("$[0].peaEligibility.status")
        .isEqualTo("UNKNOWN");
    assertThat(response).bodyJson().extractingPath("$[0].peaEligibility.source").isEqualTo("RULE");
  }

  @Test
  void detailsCarryThePeaEligibility() {
    given(queries.details(airLiquide.id()))
        .willReturn(new MarketQueries.InstrumentDetails(airLiquide, null));

    var response = mvc.get().uri(INSTRUMENTS + "/" + airLiquide.id()).with(user(USER)).exchange();

    assertThat(response).bodyJson().extractingPath("$.peaEligibility.status").isEqualTo("UNKNOWN");
  }

  @Test
  void blankOrTooLongQueryIsAValidationError() {
    for (String query : List.of(" ", "x".repeat(101))) {
      var response = mvc.get().uri(INSTRUMENTS).param("query", query).with(user(USER)).exchange();

      assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
      assertThat(response).bodyJson().extractingPath("$.code").isEqualTo("validation.failed");
      assertThat(response).bodyJson().extractingPath("$.errors[0].field").isEqualTo("query");
    }
  }

  @Test
  void detailsCarryTheLastCloseAsDecimalText() {
    var close =
        new DailyClose(
            airLiquide.id(),
            LocalDate.parse(FRIDAY),
            new BigDecimal("173.16"),
            EUR,
            PriceSource.EURONEXT,
            812_345L,
            4_321);
    given(queries.details(airLiquide.id()))
        .willReturn(new MarketQueries.InstrumentDetails(airLiquide, close));

    var response = mvc.get().uri(INSTRUMENTS + "/" + airLiquide.id()).with(user(USER)).exchange();

    assertThat(response).hasStatusOk();
    assertThat(response)
        .bodyJson()
        .extractingPath("$.trackedSince")
        .isEqualTo("2026-10-19T08:00:00Z");
    assertThat(response).bodyJson().extractingPath("$.historyLoaded").isEqualTo(true);
    assertThat(response).bodyJson().extractingPath("$.lastClose.date").isEqualTo(FRIDAY);
    assertThat(response).bodyJson().extractingPath("$.lastClose.close").isEqualTo("173.1600");
    assertThat(response).bodyJson().extractingPath("$.lastClose.currency").isEqualTo("EUR");
    assertThat(response).bodyJson().extractingPath("$.lastClose.source").isEqualTo("EURONEXT");
    assertThat(response).bodyJson().extractingPath("$.lastClose.volume").isEqualTo(812_345);
    assertThat(response).bodyJson().extractingPath("$.lastClose.tradeCount").isEqualTo(4_321);
  }

  @Test
  void instrumentWithoutCloseHasNoLastClose() {
    Instrument untracked =
        new Instrument(
            airLiquide.id(),
            airLiquide.listing(),
            "AI",
            "Air Liquide",
            InstrumentType.STOCK,
            EUR,
            null,
            false,
            MarketTestData.UNKNOWN_BY_RULE);
    given(queries.details(untracked.id()))
        .willReturn(new MarketQueries.InstrumentDetails(untracked, null));

    var response = mvc.get().uri(INSTRUMENTS + "/" + untracked.id()).with(user(USER)).exchange();

    assertThat(response).bodyJson().doesNotHavePath("$.lastClose");
    assertThat(response).bodyJson().doesNotHavePath("$.trackedSince");
  }

  @Test
  void unknownInstrumentIsNotFound() {
    given(queries.details(airLiquide.id()))
        .willThrow(new ResourceNotFoundException(InstrumentTracking.NOT_FOUND, "unknown"));

    var response = mvc.get().uri(INSTRUMENTS + "/" + airLiquide.id()).with(user(USER)).exchange();

    assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(response).bodyJson().extractingPath("$.code").isEqualTo("instrument.not-found");
  }

  @Test
  void pricesAreDecimalTextsWithTheirCurrency() {
    LocalDate from = LocalDate.parse("2026-10-01");
    LocalDate to = LocalDate.parse(FRIDAY);
    given(queries.closes(airLiquide.id(), from, to))
        .willReturn(
            List.of(
                new DailyClose(
                    airLiquide.id(), to, new BigDecimal("709.55"), EUR, PriceSource.EODHD)));

    var response =
        mvc.get()
            .uri(INSTRUMENTS + "/" + airLiquide.id() + "/prices?from=2026-10-01&to=2026-10-16")
            .with(user(USER))
            .exchange();

    assertThat(response).hasStatusOk();
    assertThat(response).bodyJson().extractingPath("$[0].date").isEqualTo(FRIDAY);
    assertThat(response).bodyJson().extractingPath("$[0].close").isEqualTo("709.5500");
    assertThat(response).bodyJson().extractingPath("$[0].source").isEqualTo("EODHD");
    assertThat(response)
        .as("unknown activity is absent, never null or zero")
        .bodyJson()
        .doesNotHavePath("$[0].volume");
    assertThat(response).bodyJson().doesNotHavePath("$[0].tradeCount");
  }

  @Test
  void pricesCarryTheVolumeWhenKnown() {
    LocalDate day = LocalDate.parse(FRIDAY);
    given(queries.closes(airLiquide.id(), null, null))
        .willReturn(
            List.of(
                new DailyClose(
                    airLiquide.id(),
                    day,
                    new BigDecimal("709.55"),
                    EUR,
                    PriceSource.EODHD,
                    9_876_543_210L,
                    null)));

    var response =
        mvc.get().uri(INSTRUMENTS + "/" + airLiquide.id() + "/prices").with(user(USER)).exchange();

    assertThat(response).bodyJson().extractingPath("$[0].volume").isEqualTo(9_876_543_210L);
    assertThat(response).bodyJson().doesNotHavePath("$[0].tradeCount");
  }

  @Test
  void periodWithoutBoundsUsesTheDefaults() {
    given(queries.closes(airLiquide.id(), null, null)).willReturn(List.of());

    var response =
        mvc.get().uri(INSTRUMENTS + "/" + airLiquide.id() + "/prices").with(user(USER)).exchange();

    assertThat(response).hasStatusOk();
    then(queries).should().closes(airLiquide.id(), null, null);
  }

  @Test
  void invalidPeriodIsAValidationErrorOnTheParameter() {
    given(queries.closes(airLiquide.id(), LocalDate.parse("2026-10-17"), LocalDate.parse(FRIDAY)))
        .willThrow(new InvalidPriceRangeException("from", InvalidPriceRangeException.NOT_AFTER_TO));

    var response =
        mvc.get()
            .uri(INSTRUMENTS + "/" + airLiquide.id() + "/prices?from=2026-10-17&to=2026-10-16")
            .with(user(USER))
            .exchange();

    assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(response).bodyJson().extractingPath("$.code").isEqualTo("validation.failed");
    assertThat(response).bodyJson().extractingPath("$.errors[0].field").isEqualTo("from");
    assertThat(response).bodyJson().extractingPath("$.errors[0].code").isEqualTo("NotAfterTo");
    assertThat(response).bodyJson().doesNotHavePath("$.detail");
  }

  @Test
  void malformedDateIsABadRequest() {
    var response =
        mvc.get()
            .uri(INSTRUMENTS + "/" + airLiquide.id() + "/prices?from=16/10/2026")
            .with(user(USER))
            .exchange();

    assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void trackingAnswersNoContent() {
    var response =
        mvc.put()
            .uri(INSTRUMENTS + "/" + airLiquide.id() + TRACKING)
            .with(user(USER))
            .with(csrf().asHeader())
            .exchange();

    assertThat(response).hasStatus(HttpStatus.NO_CONTENT);
    then(tracking).should().track(airLiquide.id());
  }

  @Test
  void trackingRequiresTheCsrfToken() {
    var response =
        mvc.put().uri(INSTRUMENTS + "/" + airLiquide.id() + TRACKING).with(user(USER)).exchange();

    assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void trackingAnUnknownInstrumentIsNotFound() {
    willThrow(new ResourceNotFoundException(InstrumentTracking.NOT_FOUND, "unknown"))
        .given(tracking)
        .track(airLiquide.id());

    var response =
        mvc.put()
            .uri(INSTRUMENTS + "/" + airLiquide.id() + TRACKING)
            .with(user(USER))
            .with(csrf().asHeader())
            .exchange();

    assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
  }
}
