package dev.investai.system.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
class StatusApiIT {

  @Autowired private MockMvcTester mvc;

  @Test
  void statusEndpointIsServedByTheFullContext() {
    var response = mvc.get().uri("/api/v1/status").exchange();

    assertThat(response).hasStatus(HttpStatus.OK);
    assertThat(response).bodyJson().extractingPath("$.application").isEqualTo("investai-backend");
  }

  @Test
  void healthEndpointIsUp() {
    var response = mvc.get().uri("/actuator/health").exchange();

    assertThat(response).hasStatus(HttpStatus.OK);
    assertThat(response).bodyJson().extractingPath("$.status").isEqualTo("UP");
  }
}
