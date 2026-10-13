package dev.investai.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

/** Cases that are hard to trigger through MockMvc: class-level constraints, missing request id. */
class ApiExceptionHandlerEdgeCasesTest {

  private static final String OBJECT_NAME = "thingRequest";

  private final ApiExceptionHandler handler = new ApiExceptionHandler();
  private final ServletWebRequest requestWithoutId =
      new ServletWebRequest(new MockHttpServletRequest("POST", "/things"));

  @Test
  void classLevelViolationsAreReportedOnTheObject() throws NoSuchMethodException {
    var bindingResult = new BeanPropertyBindingResult(new Object(), OBJECT_NAME);
    bindingResult.addError(
        new ObjectError(OBJECT_NAME, new String[] {"ValidRange"}, null, "invalid range"));
    bindingResult.addError(new ObjectError(OBJECT_NAME, new String[0], null, "no code"));
    var parameter = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);
    var exception = new MethodArgumentNotValidException(parameter, bindingResult);

    var response =
        Objects.requireNonNull(
            handler.handleMethodArgumentNotValid(
                exception, new HttpHeaders(), HttpStatus.BAD_REQUEST, requestWithoutId));

    assertThat(response.getBody())
        .isInstanceOfSatisfying(
            ProblemDetail.class,
            problem ->
                assertThat(problem.getProperties())
                    .containsEntry(
                        "errors",
                        List.of(
                            new FieldViolation(OBJECT_NAME, "ValidRange"),
                            new FieldViolation(OBJECT_NAME, "Invalid")))
                    .doesNotContainKey("requestId"));
  }

  @Test
  void nonProblemBodiesAreLeftUntouched() {
    var response =
        handler.createResponseEntity(
            Map.of("legacy", true), new HttpHeaders(), HttpStatus.BAD_REQUEST, requestWithoutId);

    assertThat(response.getBody()).isEqualTo(Map.of("legacy", true));
  }
}
