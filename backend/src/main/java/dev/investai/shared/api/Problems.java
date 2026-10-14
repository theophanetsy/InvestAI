package dev.investai.shared.api;

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
}
