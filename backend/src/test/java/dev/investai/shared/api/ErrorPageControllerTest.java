package dev.investai.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.investai.support.SecuredWebMvcTest;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Errors raised outside Spring MVC (filters, container) reach {@code /error}: same contract. */
@SecuredWebMvcTest(ErrorPageController.class)
class ErrorPageControllerTest {

  private static final String ERROR_PATH = "/error";
  private static final String CODE = "$.code";
  private static final String INTERNAL_ERROR = "internal.error";
  private static final String USER = "someone";

  @Autowired private MockMvcTester mvc;

  @Test
  void containerErrorBecomesProblemWithGenericCode() {
    var response =
        mvc.get()
            .uri(ERROR_PATH)
            .with(user(USER))
            .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
            .exchange();

    assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(response).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("http.bad-request");
    assertThat(response).bodyJson().doesNotHavePath("$.detail");
    assertThat(response)
        .bodyJson()
        .extractingPath("$.requestId")
        .isEqualTo(response.getResponse().getHeader(RequestIdFilter.HEADER));
  }

  @Test
  void serverErrorUsesTheInternalErrorCode() {
    var response =
        mvc.get()
            .uri(ERROR_PATH)
            .with(user(USER))
            .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 503)
            .exchange();

    assertThat(response).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo(INTERNAL_ERROR);
  }

  @Test
  void errorOfModifyingRequestIsAlsoProblem() {
    var response =
        mvc.post()
            .uri(ERROR_PATH)
            .with(user(USER))
            .with(csrf().asHeader())
            .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 413)
            .exchange();

    assertThat(response).hasStatus(HttpStatus.CONTENT_TOO_LARGE);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("http.content-too-large");
  }

  @Test
  void missingStatusIsTreatedAsServerError() {
    var response = mvc.get().uri(ERROR_PATH).with(user(USER)).exchange();

    assertThat(response).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo(INTERNAL_ERROR);
  }
}
