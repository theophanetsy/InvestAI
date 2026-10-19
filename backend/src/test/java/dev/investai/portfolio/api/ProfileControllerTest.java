package dev.investai.portfolio.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.investai.portfolio.application.InvestorProfiles;
import dev.investai.portfolio.application.ProfileChange;
import dev.investai.portfolio.domain.InvestorProfile;
import dev.investai.portfolio.domain.RiskProfile;
import dev.investai.shared.domain.ResourceNotFoundException;
import dev.investai.support.SecuredWebMvcTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Investor profile API (spec 2.3, criteria 1, 2 and 9). */
@SecuredWebMvcTest(ProfileController.class)
class ProfileControllerTest {

  private static final String PROFILE = "/api/v1/profile";
  private static final String USER = "someone";
  private static final String VALID =
      """
      {"amountToInvest":"20000","horizonYears":10,"riskProfile":"DYNAMIC","exclusions":[]}
      """;

  @Autowired private MockMvcTester mvc;
  @MockitoBean private InvestorProfiles profiles;

  private final InvestorProfile stored =
      new InvestorProfile(
          new BigDecimal("20000"),
          10,
          RiskProfile.DYNAMIC,
          List.of("Tabac"),
          "Retraite",
          LocalDate.parse("2019-03-01"),
          Instant.parse("2026-10-19T08:00:00Z"));

  @Test
  void profileRequiresASession() {
    assertThat(mvc.get().uri(PROFILE).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(put(VALID, false)).hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void profileIsReturnedWithItsAmountAsDecimalText() {
    given(profiles.get()).willReturn(stored);

    var response = mvc.get().uri(PROFILE).with(user(USER)).exchange();

    assertThat(response).hasStatusOk();
    var json = assertThat(response).bodyJson();
    json.extractingPath("$.amountToInvest").isEqualTo("20000.00");
    json.extractingPath("$.currency").isEqualTo("EUR");
    json.extractingPath("$.horizonYears").isEqualTo(10);
    json.extractingPath("$.riskProfile").isEqualTo("DYNAMIC");
    json.extractingPath("$.exclusions[0]").isEqualTo("Tabac");
    json.extractingPath("$.objectives").isEqualTo("Retraite");
    json.extractingPath("$.peaOpenedOn").isEqualTo("2019-03-01");
    json.extractingPath("$.updatedAt").isEqualTo("2026-10-19T08:00:00Z");
  }

  @Test
  void missingProfileIsNotFound() {
    given(profiles.get())
        .willThrow(new ResourceNotFoundException(InvestorProfiles.NOT_FOUND, "none"));

    var response = mvc.get().uri(PROFILE).with(user(USER)).exchange();

    assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(response).bodyJson().extractingPath("$.code").isEqualTo("profile.not-found");
  }

  @Test
  void savingConvertsTheAmountExactly() {
    given(profiles.save(any())).willReturn(stored);
    var change = ArgumentCaptor.forClass(ProfileChange.class);

    var response =
        put(
            """
            {"amountToInvest":"20000.10","horizonYears":10,"riskProfile":"DYNAMIC",
             "exclusions":["Tabac"],"objectives":"ligne 1\\nligne 2\\tfin"}
            """,
            true);

    assertThat(response).hasStatusOk();
    Mockito.verify(profiles).save(change.capture());
    assertThat(change.getValue().amountToInvest()).isEqualTo(new BigDecimal("20000.10"));
    assertThat(change.getValue().objectives()).isEqualTo("ligne 1\nligne 2\tfin");
    assertThat(change.getValue().peaOpenedOn()).isNull();
  }

  @Test
  void riskProfilesAreListedWithTheirFrenchText() {
    var response = mvc.get().uri(PROFILE + "/risk-profiles").with(user(USER)).exchange();

    assertThat(response).hasStatusOk();
    var json = assertThat(response).bodyJson();
    json.extractingPath("$.length()").isEqualTo(4);
    json.extractingPath("$[0].code").isEqualTo("PRUDENT");
    json.extractingPath("$[1].label").isEqualTo("Équilibré");
    json.extractingPath("$[3].description").isEqualTo(RiskProfile.AGGRESSIVE.description());
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "\"amountToInvest\":\"0\"                 | amountToInvest | DecimalMin",
        "\"amountToInvest\":\"10,5\"              | amountToInvest | Pattern",
        "\"amountToInvest\":\"10.001\"            | amountToInvest | Pattern",
        "\"amountToInvest\":null                  | amountToInvest | NotNull",
        "\"horizonYears\":0                       | horizonYears   | Min",
        "\"horizonYears\":41                      | horizonYears   | Max",
        "\"riskProfile\":null                     | riskProfile    | NotNull",
        "\"exclusions\":[\" \"]                   | exclusions[0]  | NotBlank",
        "\"exclusions\":[\"a\\nb\"]               | exclusions[0]  | Pattern",
        "\"objectives\":\"bell\\u0007\"           | objectives     | Pattern",
        "\"peaOpenedOn\":\"2999-01-01\"           | peaOpenedOn    | PastOrPresent",
      })
  void invalidFieldIsRejectedWithItsName(String override, String field, String constraint) {
    String body = withField(override);

    var response = put(body, true);

    assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(response).bodyJson().extractingPath("$.code").isEqualTo("validation.failed");
    assertThat(response).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
    assertThat(response)
        .bodyJson()
        .extractingPath("$.errors[*].code")
        .asArray()
        .contains(constraint);
  }

  @Test
  void objectivesAndExclusionsAreBounded() {
    var tooLong = put(withField("\"objectives\":\"" + "x".repeat(501) + "\""), true);
    var tooMany = put(withField("\"exclusions\":[" + "\"a\",".repeat(20) + "\"a\"]"), true);

    assertThat(tooLong).bodyJson().extractingPath("$.errors[0].field").isEqualTo("objectives");
    assertThat(tooMany).bodyJson().extractingPath("$.errors[0].field").isEqualTo("exclusions");
  }

  @Test
  void absentExclusionsMeanNone() {
    given(profiles.save(any())).willReturn(stored);
    var change = ArgumentCaptor.forClass(ProfileChange.class);

    var response =
        put("{\"amountToInvest\":\"1\",\"horizonYears\":1,\"riskProfile\":\"PRUDENT\"}", true);

    assertThat(response).hasStatusOk();
    Mockito.verify(profiles).save(change.capture());
    assertThat(change.getValue().exclusions()).isEmpty();
  }

  @Test
  void unknownRiskProfileIsABadRequest() {
    assertThat(put(VALID.replace("DYNAMIC", "YOLO"), true)).hasStatus(HttpStatus.BAD_REQUEST);
  }

  /** The valid body with one more field, which replaces the same field if present (last wins). */
  private static String withField(String field) {
    String valid = VALID.strip();
    return valid.substring(0, valid.length() - 1) + "," + field + "}";
  }

  private MvcTestResult put(String body, boolean withCsrf) {
    var request =
        mvc.put()
            .uri(PROFILE)
            .with(user(USER))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    return withCsrf ? request.with(csrf().asHeader()).exchange() : request.exchange();
  }
}
