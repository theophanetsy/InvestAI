package dev.investai.portfolio.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.investai.market.domain.InstrumentId;
import dev.investai.portfolio.application.PortfolioCommands;
import dev.investai.portfolio.domain.PortfolioLine;
import dev.investai.portfolio.domain.PortfolioLineId;
import dev.investai.shared.domain.BusinessRuleException;
import dev.investai.shared.domain.ResourceNotFoundException;
import dev.investai.support.SecuredWebMvcTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Declared lines API (spec 2.3, criteria 3, 5 and 9). */
@SecuredWebMvcTest(PortfolioLineController.class)
class PortfolioLineControllerTest {

  private static final String LINES = "/api/v1/portfolio/lines";
  private static final String USER = "someone";
  private static final String INSTRUMENT = "7d0f0a52-3c3e-4f4e-9d4e-0b9c3f1a2b3c";

  @Autowired private MockMvcTester mvc;
  @MockitoBean private PortfolioCommands commands;

  private final InstrumentId instrumentId = new InstrumentId(UUID.fromString(INSTRUMENT));
  private final PortfolioLine line =
      new PortfolioLine(
          PortfolioLineId.newId(),
          instrumentId,
          10,
          new BigDecimal("150.1234"),
          Instant.parse("2026-10-19T08:00:00Z"));

  @Test
  void addingALineAnswersCreatedWithTheDeclaredValues() {
    given(commands.addLine(instrumentId, 10, new BigDecimal("150.1234"))).willReturn(line);

    var response =
        send(mvc.post().uri(LINES), lineBody("\"quantity\":10,\"averagePrice\":\"150.1234\""));

    assertThat(response).hasStatus(HttpStatus.CREATED);
    var json = assertThat(response).bodyJson();
    json.extractingPath("$.id").isEqualTo(line.id().toString());
    json.extractingPath("$.instrumentId").isEqualTo(INSTRUMENT);
    json.extractingPath("$.averagePrice").isEqualTo("150.1234");
    json.extractingPath("$.updatedAt").isEqualTo("2026-10-19T08:00:00Z");
  }

  @Test
  void averagePriceIsOptional() {
    given(commands.addLine(instrumentId, 3, null))
        .willReturn(line.update(3, null, line.updatedAt()));

    var response = send(mvc.post().uri(LINES), lineBody("\"quantity\":3"));

    assertThat(response).hasStatus(HttpStatus.CREATED);
    assertThat(response).bodyJson().doesNotHavePath("$.averagePrice");
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "\"quantity\":0                | quantity     | Min",
        "\"quantity\":1000000001       | quantity     | Max",
        "\"averagePrice\":\"0\"        | averagePrice | Pattern",
        "\"averagePrice\":\"1.00001\"  | averagePrice | Pattern",
        "\"averagePrice\":\"1e3\"      | averagePrice | Pattern",
        "\"instrumentId\":null         | instrumentId | NotNull",
      })
  void invalidLineIsRejectedWithItsField(String override, String field, String constraint) {
    String body = lineBody("\"quantity\":1," + override);

    var response = send(mvc.post().uri(LINES), body);

    assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(response).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
    assertThat(response).bodyJson().extractingPath("$.errors[0].code").isEqualTo(constraint);
  }

  @Test
  void fractionalQuantityIsRejectedNotTruncated() {
    var response = send(mvc.post().uri(LINES), lineBody("\"quantity\":1.5"));

    assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    Mockito.verifyNoInteractions(commands);
  }

  @Test
  void businessRulesAreUnprocessable() {
    given(commands.addLine(instrumentId, 1, null))
        .willThrow(new BusinessRuleException(PortfolioCommands.DUPLICATE_INSTRUMENT, "dup"));

    var response = send(mvc.post().uri(LINES), lineBody("\"quantity\":1"));

    assertThat(response).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("portfolio.line.duplicate-instrument");
  }

  @Test
  void lineIsUpdatedById() {
    var updated = line.update(12, null, line.updatedAt());
    given(commands.updateLine(line.id(), 12, null)).willReturn(updated);

    var response = send(mvc.put().uri(LINES + "/" + line.id()), "{\"quantity\":12}");

    assertThat(response).hasStatusOk();
    assertThat(response).bodyJson().extractingPath("$.quantity").isEqualTo(12);
  }

  @Test
  void lineIsDeletedById() {
    var response =
        mvc.delete()
            .uri(LINES + "/" + line.id())
            .with(user(USER))
            .with(csrf().asHeader())
            .exchange();

    assertThat(response).hasStatus(HttpStatus.NO_CONTENT);
    Mockito.verify(commands).removeLine(line.id());
  }

  @Test
  void unknownLineIsNotFound() {
    willThrow(new ResourceNotFoundException(PortfolioCommands.LINE_NOT_FOUND, "unknown"))
        .given(commands)
        .removeLine(line.id());

    var response =
        mvc.delete()
            .uri(LINES + "/" + line.id())
            .with(user(USER))
            .with(csrf().asHeader())
            .exchange();

    assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(response).bodyJson().extractingPath("$.code").isEqualTo("portfolio.line.not-found");
  }

  @Test
  void changesRequireASessionAndTheCsrfToken() {
    assertThat(mvc.delete().uri(LINES + "/" + line.id()).with(user(USER)).exchange())
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(mvc.delete().uri(LINES + "/" + line.id()).with(csrf().asHeader()).exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void parsingIsNeverReachedWithAnInvalidNumber() {
    assertThatThrownBy(() -> InputFormats.parse("1,5"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(InputFormats.parse("1.50")).isEqualTo(new BigDecimal("1.50"));
  }

  private static String lineBody(String fields) {
    return "{\"instrumentId\":\"" + INSTRUMENT + "\"," + fields + "}";
  }

  private MvcTestResult send(MockMvcTester.MockMvcRequestBuilder request, String body) {
    return request
        .with(user(USER))
        .with(csrf().asHeader())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }
}
