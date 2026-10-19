package dev.investai.market.api;

import static dev.investai.market.MarketTestData.isin;
import static dev.investai.market.MarketTestData.listed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.investai.market.MarketTestData;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.MarketDataProvider;
import dev.investai.market.domain.PriceSource;
import dev.investai.market.infrastructure.MarketJobs;
import dev.investai.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Market API on the real database and security configuration (spec 2.1, criterion 17). */
@IntegrationTest
class MarketApiIT {

  private static final String INSTRUMENTS = "/api/v1/market/instruments/";
  private static final String USER = "someone";

  @Autowired private MockMvcTester mvc;
  @Autowired private JdbcClient jdbc;
  @Autowired private InstrumentRepository instruments;
  @Autowired private DailyPriceRepository prices;
  @Autowired private ApplicationContext context;
  @Autowired private MarketDataProvider provider;

  private final LocalDate today = LocalDate.now(ZoneId.of("Europe/Paris"));
  private Instrument societe;

  @BeforeEach
  void setUp() {
    MarketTestData.clearTables(jdbc);
    instruments.upsertReference(
        List.of(
            listed(isin("FR000013030"), "GLE", "Société Générale"),
            listed(isin("FR000012007"), "AI", "Air Liquide")),
        Instant.now());
    societe = instruments.search("GLE", 1).getFirst();
    prices.save(
        List.of(close(today.minusDays(400), "30.10"), close(today.minusDays(3), "31.25")),
        Instant.now());
  }

  @Test
  void searchIgnoresCaseAndAccents() {
    var response =
        mvc.get().uri("/api/v1/market/instruments?query=SOCIETE").with(user(USER)).exchange();

    assertThat(response).hasStatusOk();
    assertThat(response).bodyJson().extractingPath("$.length()").isEqualTo(1);
    assertThat(response).bodyJson().extractingPath("$[0].name").isEqualTo("Société Générale");
    assertThat(response).bodyJson().extractingPath("$[0].tracked").isEqualTo(false);
  }

  @Test
  void detailsGiveTheLatestClose() {
    var response = mvc.get().uri(INSTRUMENTS + societe.id()).with(user(USER)).exchange();

    assertThat(response).hasStatusOk();
    assertThat(response).bodyJson().extractingPath("$.lastClose.close").isEqualTo("31.2500");
    assertThat(response)
        .bodyJson()
        .extractingPath("$.lastClose.date")
        .isEqualTo(today.minusDays(3).toString());
  }

  @Test
  void pricesOfAnUntrackedInstrumentDefaultToTheLastYearInDateOrderWithTheirActivity() {
    prices.save(
        List.of(
            new DailyClose(
                societe.id(),
                today.minusDays(10),
                new BigDecimal("29.00"),
                MarketTestData.EUR,
                PriceSource.EURONEXT,
                1_500L,
                12)),
        Instant.now());

    var response =
        mvc.get().uri(INSTRUMENTS + societe.id() + "/prices").with(user(USER)).exchange();

    assertThat(response).hasStatusOk();
    assertThat(response).bodyJson().extractingPath("$.length()").isEqualTo(2);
    assertThat(response).bodyJson().extractingPath("$[0].close").isEqualTo("29.0000");
    assertThat(response).bodyJson().extractingPath("$[1].close").isEqualTo("31.2500");
    assertThat(response).bodyJson().extractingPath("$[0].volume").isEqualTo(1_500);
    assertThat(response).bodyJson().extractingPath("$[0].tradeCount").isEqualTo(12);
    assertThat(response).bodyJson().doesNotHavePath("$[1].volume");
    assertThat(societe.tracked()).isFalse();
  }

  @Test
  void invalidPeriodIsRejected() {
    var reversed =
        mvc.get()
            .uri(INSTRUMENTS + societe.id() + "/prices?from=2026-10-17&to=2026-10-16")
            .with(user(USER))
            .exchange();
    var tooLong =
        mvc.get()
            .uri(INSTRUMENTS + societe.id() + "/prices?from=2020-01-01&to=2026-10-16")
            .with(user(USER))
            .exchange();

    assertThat(reversed).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(reversed).bodyJson().extractingPath("$.errors[0].code").isEqualTo("NotAfterTo");
    assertThat(tooLong).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(tooLong).bodyJson().extractingPath("$.errors[0].code").isEqualTo("MaxFiveYears");
    assertThat(reversed).bodyJson().extractingPath("$.requestId").isNotNull();
  }

  @Test
  void unknownInstrumentIsNotFound() {
    var response =
        mvc.get()
            .uri(INSTRUMENTS + "00000000-0000-0000-0000-000000000000")
            .with(user(USER))
            .exchange();

    assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(response).bodyJson().extractingPath("$.code").isEqualTo("instrument.not-found");
  }

  @Test
  void trackingIsIdempotentAndCallsNoProvider() {
    // Real CSRF exchange, like the browser (csrf() would break the shared filter, see CLAUDE.md).
    Cookie csrf =
        Objects.requireNonNull(
            mvc.get().uri("/api/v1/auth/me").exchange().getResponse().getCookie("XSRF-TOKEN"));

    for (int attempt = 0; attempt < 2; attempt++) {
      var response =
          mvc.put()
              .uri(INSTRUMENTS + societe.id() + "/tracking")
              .with(user(USER))
              .cookie(csrf)
              .header("X-XSRF-TOKEN", csrf.getValue())
              .exchange();
      assertThat(response).hasStatus(HttpStatus.NO_CONTENT);
    }

    assertThat(instruments.findById(societe.id()))
        .hasValueSatisfying(
            found -> {
              assertThat(found.tracked()).isTrue();
              assertThat(found.historyLoaded()).isFalse();
            });
  }

  @Test
  void testsRunWithoutJobsNorEodhdKey() {
    assertThat(context.getBeansOfType(MarketJobs.class)).isEmpty();
    assertThat(provider.isEnabled()).isFalse();
  }

  private DailyClose close(LocalDate day, String price) {
    return new DailyClose(
        societe.id(), day, new BigDecimal(price), MarketTestData.EUR, PriceSource.EURONEXT);
  }
}
