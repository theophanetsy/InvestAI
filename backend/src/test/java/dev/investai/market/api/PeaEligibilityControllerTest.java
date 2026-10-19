package dev.investai.market.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.investai.market.application.InstrumentTracking;
import dev.investai.market.application.PeaEligibilityCorrection;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.PeaEligibility;
import dev.investai.market.domain.PeaEligibilityStatus;
import dev.investai.shared.domain.ResourceNotFoundException;
import dev.investai.support.SecuredWebMvcTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Owner's correction of the PEA eligibility (spec 2.3, criterion 7). */
@SecuredWebMvcTest(PeaEligibilityController.class)
class PeaEligibilityControllerTest {

  private static final String INSTRUMENTS = "/api/v1/market/instruments";
  private static final String PEA_ELIGIBILITY = "/pea-eligibility";
  private static final String USER = "someone";

  @Autowired private MockMvcTester mvc;
  @MockitoBean private PeaEligibilityCorrection eligibility;

  private final InstrumentId airLiquide =
      new InstrumentId(UUID.fromString("7d0f0a52-3c3e-4f4e-9d4e-0b9c3f1a2b3c"));

  @Test
  void ownerCorrectsThePeaEligibility() {
    given(eligibility.correct(airLiquide, PeaEligibilityStatus.NOT_ELIGIBLE))
        .willReturn(PeaEligibility.byOwner(PeaEligibilityStatus.NOT_ELIGIBLE));

    var response = correct("{\"status\":\"NOT_ELIGIBLE\"}");

    assertThat(response).hasStatusOk();
    assertThat(response).bodyJson().extractingPath("$.status").isEqualTo("NOT_ELIGIBLE");
    assertThat(response).bodyJson().extractingPath("$.source").isEqualTo("OWNER");
  }

  @Test
  void correctionWithoutStatusOrWithAnUnknownOneIsRejected() {
    var missing = correct("{}");
    var unknown = correct("{\"status\":\"MAYBE\"}");

    assertThat(missing).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(missing).bodyJson().extractingPath("$.errors[0].field").isEqualTo("status");
    assertThat(unknown).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void correctionRequiresTheCsrfTokenAndASession() {
    String uri = INSTRUMENTS + "/" + airLiquide + PEA_ELIGIBILITY;
    String body = "{\"status\":\"ELIGIBLE\"}";

    assertThat(
            mvc.put()
                .uri(uri)
                .with(user(USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange())
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(
            mvc.put()
                .uri(uri)
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void correctingAnUnknownInstrumentIsNotFound() {
    given(eligibility.correct(airLiquide, PeaEligibilityStatus.ELIGIBLE))
        .willThrow(new ResourceNotFoundException(InstrumentTracking.NOT_FOUND, "unknown"));

    var response = correct("{\"status\":\"ELIGIBLE\"}");

    assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(response).bodyJson().extractingPath("$.code").isEqualTo("instrument.not-found");
  }

  private MvcTestResult correct(String body) {
    return mvc.put()
        .uri(INSTRUMENTS + "/" + airLiquide + PEA_ELIGIBILITY)
        .with(user(USER))
        .with(csrf().asHeader())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }
}
