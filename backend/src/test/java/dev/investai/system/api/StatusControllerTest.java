package dev.investai.system.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import dev.investai.system.application.ApplicationStatus;
import dev.investai.system.application.StatusService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(StatusController.class)
class StatusControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private StatusService statusService;

  @Test
  void statusReturnsServiceSnapshot() {
    given(statusService.currentStatus())
        .willReturn(new ApplicationStatus("investai", Instant.parse("2026-01-02T03:04:05Z")));

    var response = mvc.get().uri("/api/v1/status").exchange();

    assertThat(response).hasStatus(HttpStatus.OK);
    assertThat(response).bodyJson().extractingPath("$.application").isEqualTo("investai");
    assertThat(response)
        .bodyJson()
        .extractingPath("$.serverTime")
        .isEqualTo("2026-01-02T03:04:05Z");
  }
}
