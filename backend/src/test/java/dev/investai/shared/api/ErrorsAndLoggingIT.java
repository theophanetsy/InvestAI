package dev.investai.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ErrorsAndLoggingIT {

  private static final String HEADER = "X-Request-Id";

  @Autowired private MockMvcTester mvc;

  @Test
  void unknownRouteIsProblemCorrelatedWithResponseHeader() {
    var response = mvc.get().uri("/api/v1/does-not-exist").exchange();

    assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(response).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response).bodyJson().doesNotHavePath("$.detail");
    assertThat(response)
        .bodyJson()
        .extractingPath("$.requestId")
        .isEqualTo(response.getResponse().getHeader(HEADER));
  }

  @Test
  void requestSummaryIsLoggedAsJsonWithRequestId(CapturedOutput output) {
    var response = mvc.get().uri("/api/v1/status").exchange();
    String requestId = response.getResponse().getHeader(HEADER);

    assertThat(output.getOut().lines())
        .filteredOn(line -> line.contains("\"requestId\":\"" + requestId + "\""))
        .singleElement()
        .satisfies(
            line ->
                assertThat(line)
                    .startsWith("{")
                    .contains("GET /api/v1/status -> 200")
                    .contains("\"service\":{\"name\":\"investai-backend\""));
  }
}
