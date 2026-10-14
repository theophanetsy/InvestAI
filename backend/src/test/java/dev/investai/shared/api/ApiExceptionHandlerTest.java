package dev.investai.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import dev.investai.shared.domain.BusinessRuleException;
import dev.investai.shared.domain.ResourceNotFoundException;
import dev.investai.support.SecuredWebMvcTest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SecuredWebMvcTest(ApiExceptionHandlerTest.FixtureController.class)
@WithMockUser
@Import(ApiExceptionHandlerTest.FixtureController.class)
@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

  private static final String INTERNAL_DETAIL = "internal-detail-7f3a";
  private static final String CODE = "$.code";
  private static final String ERRORS = "$.errors";
  private static final String THINGS = "/fixture/things";
  private static final String PAGE_SIZE = "page-size";

  @Autowired private MockMvcTester mvc;

  @Test
  void resourceNotFoundBecomes404WithStableCode() {
    var response = mvc.get().uri("/fixture/not-found").exchange();

    assertProblem(response, HttpStatus.NOT_FOUND);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("thing.not-found");
    assertThat(response).bodyJson().extractingPath("$.instance").isEqualTo("/fixture/not-found");
    assertThat(response).bodyJson().doesNotHavePath("$.detail");
    assertThat(response).bodyText().doesNotContain(INTERNAL_DETAIL);
  }

  @Test
  void businessRuleViolationBecomes422WithStableCode() {
    var response = mvc.get().uri("/fixture/rule").exchange();

    assertProblem(response, HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("thing.limit-exceeded");
    assertThat(response).bodyJson().extractingPath("$.instance").isEqualTo("/fixture/rule");
    assertThat(response).bodyText().doesNotContain(INTERNAL_DETAIL);
  }

  @Test
  void invalidBodyListsViolationsWithoutRejectedValues() {
    var response =
        mvc.post()
            .uri(THINGS)
            .with(csrf().asHeader())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + INTERNAL_DETAIL + "\",\"comment\":\" \"}")
            .exchange();

    assertProblem(response, HttpStatus.BAD_REQUEST);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("validation.failed");
    assertThat(response)
        .bodyJson()
        .extractingPath(ERRORS)
        .asArray()
        .containsExactlyInAnyOrder(violation("name", "Size"), violation("comment", "NotBlank"));
    assertThat(response).bodyText().doesNotContain(INTERNAL_DETAIL);
  }

  @Test
  void invalidRequestParameterIsReportedUnderItsHttpName() {
    var response = mvc.get().uri(THINGS).param(PAGE_SIZE, "1000").exchange();

    assertProblem(response, HttpStatus.BAD_REQUEST);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("validation.failed");
    assertThat(response)
        .bodyJson()
        .extractingPath(ERRORS)
        .asArray()
        .containsExactly(violation(PAGE_SIZE, "Max"));
    assertThat(response).bodyText().doesNotContain("1000");
  }

  @Test
  void unnamedRequestParameterIsReportedUnderItsJavaName() {
    var response = mvc.get().uri(THINGS).param(PAGE_SIZE, "10").param("page", "0").exchange();

    assertProblem(response, HttpStatus.BAD_REQUEST);
    assertThat(response)
        .bodyJson()
        .extractingPath(ERRORS)
        .asArray()
        .containsExactly(violation("page", "Min"));
  }

  @Test
  void invalidPathVariableAndBodyAreReportedFieldByField() {
    var response =
        mvc.put()
            .uri(THINGS + "/-1")
            .with(csrf().asHeader())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"ok\",\"comment\":\" \"}")
            .exchange();

    assertProblem(response, HttpStatus.BAD_REQUEST);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("validation.failed");
    assertThat(response)
        .bodyJson()
        .extractingPath(ERRORS)
        .asArray()
        .containsExactlyInAnyOrder(violation("id", "Positive"), violation("comment", "NotBlank"));
  }

  @Test
  void parameterOfWrongTypeBecomesGenericBadRequest() {
    var response = mvc.get().uri(THINGS).param(PAGE_SIZE, "abc").exchange();

    assertProblem(response, HttpStatus.BAD_REQUEST);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("http.bad-request");
  }

  @Test
  void malformedJsonBecomesProblem() {
    var response =
        mvc.post()
            .uri(THINGS)
            .with(csrf().asHeader())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{not json")
            .exchange();

    assertProblem(response, HttpStatus.BAD_REQUEST);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("http.bad-request");
  }

  @Test
  void unsupportedMethodBecomesProblem() {
    var response = mvc.delete().uri(THINGS).with(csrf().asHeader()).exchange();

    assertProblem(response, HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("http.method-not-allowed");
  }

  @Test
  void unknownRouteBecomesProblem() {
    var response = mvc.get().uri("/fixture/does-not-exist").exchange();

    assertProblem(response, HttpStatus.NOT_FOUND);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("http.not-found");
  }

  @Test
  void staticResourcesAreNotServed() {
    // src/test/resources/static/probe.txt exists: Spring Boot would serve it by default.
    var response = mvc.get().uri("/probe.txt").exchange();

    assertProblem(response, HttpStatus.NOT_FOUND);
  }

  @Test
  void unexpectedExceptionHidesDetailsButLogsThem(CapturedOutput output) {
    var response = mvc.get().uri("/fixture/boom").exchange();

    assertProblem(response, HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("internal.error");
    assertThat(response).bodyJson().doesNotHavePath("$.detail");
    assertThat(response)
        .bodyText()
        .doesNotContain(INTERNAL_DETAIL)
        .doesNotContain("IllegalStateException");
    String requestId = response.getResponse().getHeader("X-Request-Id");
    assertThat(output.getOut().lines())
        .filteredOn(line -> line.contains("Unhandled exception"))
        .singleElement()
        .satisfies(
            line ->
                assertThat(line)
                    .contains("\"requestId\":\"" + requestId + "\"")
                    .contains(INTERNAL_DETAIL)
                    .contains("at dev.investai.shared.api"));
  }

  @Test
  void accessDeniedInControllerIsLeftToSpringSecurity() {
    var response = mvc.get().uri("/fixture/denied").exchange();

    assertProblem(response, HttpStatus.FORBIDDEN);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("auth.forbidden");
    assertThat(response).bodyText().doesNotContain(INTERNAL_DETAIL);
  }

  @Test
  void authenticationFailureInControllerIsLeftToSpringSecurity() {
    var response = mvc.get().uri("/fixture/unauthenticated").exchange();

    assertProblem(response, HttpStatus.UNAUTHORIZED);
    assertThat(response).bodyJson().extractingPath(CODE).isEqualTo("auth.required");
  }

  private static Map<String, String> violation(String field, String code) {
    return Map.of("field", field, "code", code);
  }

  private static void assertProblem(MvcTestResult response, HttpStatus status) {
    assertThat(response).hasStatus(status);
    assertThat(response).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response).bodyJson().extractingPath("$.status").isEqualTo(status.value());
    assertThat(response).bodyJson().extractingPath("$.title").isEqualTo(status.getReasonPhrase());
    assertThat(response).bodyJson().doesNotHavePath("$.detail");
    assertThat(response)
        .bodyJson()
        .extractingPath("$.requestId")
        .isEqualTo(response.getResponse().getHeader("X-Request-Id"));
  }

  record ThingRequest(@Size(max = 10) @Nullable String name, @NotBlank @Nullable String comment) {}

  @RestController
  @RequestMapping("/fixture")
  static class FixtureController {

    @GetMapping("/not-found")
    void notFound() {
      throw new ResourceNotFoundException(
          "thing.not-found", "Thing 42 not found: " + INTERNAL_DETAIL);
    }

    @GetMapping("/rule")
    void rule() {
      throw new BusinessRuleException("thing.limit-exceeded", "Limit exceeded: " + INTERNAL_DETAIL);
    }

    @PostMapping("/things")
    void create(@Valid @RequestBody ThingRequest request) {
      // Only validation matters here.
    }

    @PutMapping("/things/{id}")
    void update(@PathVariable @Positive long id, @Valid @RequestBody ThingRequest request) {
      // Only validation matters here.
    }

    @GetMapping("/things")
    List<String> list(
        @RequestParam(PAGE_SIZE) @Max(100) int pageSize,
        @RequestParam(defaultValue = "1") @Min(1) int page) {
      return List.of(String.valueOf(pageSize), String.valueOf(page));
    }

    @GetMapping("/denied")
    void denied() {
      throw new AccessDeniedException("Not allowed: " + INTERNAL_DETAIL);
    }

    @GetMapping("/unauthenticated")
    void unauthenticated() {
      throw new InsufficientAuthenticationException("Session gone: " + INTERNAL_DETAIL);
    }

    @GetMapping("/boom")
    void boom() {
      throw new IllegalStateException("Connection pool exhausted: " + INTERNAL_DETAIL);
    }
  }
}
