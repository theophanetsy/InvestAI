package dev.investai.system.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.investai.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class StatusApiIT {

  private static final String READINESS = "/actuator/health/readiness";

  @Autowired private MockMvcTester mvc;

  @Test
  void statusEndpointIsServedByTheFullContext() {
    var response = mvc.get().uri("/api/v1/status").exchange();

    assertThat(response).hasStatus(HttpStatus.OK);
    assertThat(response).bodyJson().extractingPath("$.application").isEqualTo("investai-backend");
  }

  @Test
  void aggregatedHealthRequiresAuthentication() {
    assertThat(mvc.get().uri("/actuator/health").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);

    var response = mvc.get().uri("/actuator/health").with(user("someone")).exchange();

    assertThat(response).hasStatus(HttpStatus.OK);
    assertThat(response).bodyJson().extractingPath("$.status").isEqualTo("UP");
  }

  @Test
  void readinessIsPublicButHidesComponentsFromAnonymousCallers() {
    var response = mvc.get().uri(READINESS).exchange();

    assertThat(response).hasStatus(HttpStatus.OK);
    assertThat(response).bodyJson().extractingPath("$.status").isEqualTo("UP");
    assertThat(response).bodyJson().doesNotHavePath("$.components");
  }

  @Test
  void readinessIncludesTheDatabaseForAuthenticatedCallers() {
    var response = mvc.get().uri(READINESS).with(user("someone")).exchange();

    assertThat(response).hasStatus(HttpStatus.OK);
    assertThat(response).bodyJson().extractingPath("$.status").isEqualTo("UP");
    assertThat(response).bodyJson().extractingPath("$.components.db.status").isEqualTo("UP");
  }

  @Test
  void livenessDoesNotDependOnTheDatabase() {
    var response = mvc.get().uri("/actuator/health/liveness").exchange();

    assertThat(response).hasStatus(HttpStatus.OK);
    assertThat(response).bodyJson().doesNotHavePath("$.components.db");
  }
}
