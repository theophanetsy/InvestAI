package dev.investai.shared.api;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * One rejected field or parameter of an API request; the rejected value is deliberately not exposed
 * (ADR-0006).
 *
 * @param field field name in the body, or parameter name as sent by the client
 * @param code constraint name, e.g. {@code NotBlank}
 */
record FieldViolation(String field, String code) {

  private static final String UNKNOWN_CONSTRAINT = "Invalid";

  /** Violations of a {@code @Valid} body or model attribute. */
  static List<FieldViolation> of(List<ObjectError> errors) {
    return errors.stream().map(FieldViolation::of).toList();
  }

  /** Violations found by method validation (constrained parameters, possibly with a body). */
  static List<FieldViolation> of(HandlerMethodValidationException ex) {
    return ex.getParameterValidationResults().stream().flatMap(FieldViolation::of).toList();
  }

  private static Stream<FieldViolation> of(ParameterValidationResult result) {
    if (result instanceof ParameterErrors errors) {
      return errors.getAllErrors().stream().map(FieldViolation::of);
    }
    String name = httpNameOf(result.getMethodParameter());
    return result.getResolvableErrors().stream()
        .map(error -> new FieldViolation(name, constraintOf(error)));
  }

  private static FieldViolation of(ObjectError error) {
    String field =
        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
    return new FieldViolation(field, constraintOf(error));
  }

  /**
   * Name the client used, e.g. {@code page-size} for {@code @RequestParam("page-size") pageSize}.
   */
  private static String httpNameOf(MethodParameter parameter) {
    RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
    if (requestParam != null) {
      String declared = requestParam.name().isEmpty() ? requestParam.value() : requestParam.name();
      if (!declared.isEmpty()) {
        return declared;
      }
    }
    return Objects.requireNonNullElse(parameter.getParameterName(), "parameter");
  }

  /** Constraint name, e.g. {@code NotBlank}: the most generic of the resolvable codes. */
  private static String constraintOf(MessageSourceResolvable error) {
    String[] codes = error.getCodes();
    return codes == null || codes.length == 0 ? UNKNOWN_CONSTRAINT : codes[codes.length - 1];
  }
}
