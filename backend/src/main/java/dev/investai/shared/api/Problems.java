package dev.investai.shared.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds problem details carrying a stable {@code code} (ADR-0006). Throw them wrapped in an {@link
 * org.springframework.web.ErrorResponseException}: {@link ApiExceptionHandler} adds the {@code
 * requestId} and strips any {@code detail}.
 */
public final class Problems {

  /** Property holding the stable, machine-readable error code. */
  public static final String CODE = "code";

  private Problems() {}

  /**
   * Creates a problem with the given status and code.
   *
   * @param status the HTTP status
   * @param code the stable error code, e.g. {@code auth.required}
   * @return the problem detail
   */
  public static ProblemDetail withCode(HttpStatusCode status, String code) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setProperty(CODE, code);
    return problem;
  }

  /**
   * A 400 {@code validation.failed} for a request parameter checked by the code rather than by Bean
   * Validation (e.g. a rule across two parameters): same contract as the other validation errors.
   *
   * @param parameter name of the parameter as sent by the client
   * @param constraint name of the violated rule, e.g. {@code NotAfterTo}
   * @return the problem detail
   */
  public static ProblemDetail invalidParameter(String parameter, String constraint) {
    ProblemDetail problem = withCode(HttpStatus.BAD_REQUEST, ApiExceptionHandler.VALIDATION_FAILED);
    problem.setProperty("errors", List.of(new FieldViolation(parameter, constraint)));
    return problem;
  }
}
