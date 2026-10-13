package dev.investai.shared.api;

import dev.investai.shared.domain.BusinessRuleException;
import dev.investai.shared.domain.DomainException;
import dev.investai.shared.domain.ResourceNotFoundException;
import dev.investai.shared.logging.LogSanitizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single translation point from exceptions to RFC 9457 problem details (ADR-0006).
 *
 * <p>Every problem carries a stable {@code code} (domain code, {@code validation.failed}, or {@code
 * http.<status>} for errors raised by Spring) and the {@code requestId}; validation errors add an
 * {@code errors} list. Exception messages are logged, never returned, and so is {@code detail}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  static final String CODE = "code";
  static final String VALIDATION_FAILED = "validation.failed";
  static final String INTERNAL_ERROR = "internal.error";

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /**
   * Missing resource: 404 exposing only the code.
   *
   * @param ex the exception
   * @param request the current request
   * @return the problem response
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public @Nullable ResponseEntity<Object> handleNotFound(
      ResourceNotFoundException ex, WebRequest request) {
    return domainProblem(ex, HttpStatus.NOT_FOUND, request);
  }

  /**
   * Business rule violation: 422 exposing only the code.
   *
   * @param ex the exception
   * @param request the current request
   * @return the problem response
   */
  @ExceptionHandler(BusinessRuleException.class)
  public @Nullable ResponseEntity<Object> handleBusinessRule(
      BusinessRuleException ex, WebRequest request) {
    return domainProblem(ex, HttpStatus.UNPROCESSABLE_CONTENT, request);
  }

  private @Nullable ResponseEntity<Object> domainProblem(
      DomainException ex, HttpStatus status, WebRequest request) {
    // The code is already validated; sanitising it too keeps FindSecBugs' taint analysis exact.
    String code = LogSanitizer.oneLine(ex.code());
    String reason = LogSanitizer.oneLine(ex.getMessage());
    LOG.info("Request rejected: code={}, reason={}", code, reason);
    return problem(ex, status, ex.code(), request);
  }

  /**
   * Last resort: hides the cause from the client and logs it with its stack trace.
   *
   * @param ex the unexpected exception
   * @param request the current request
   * @return a generic 500 problem response
   */
  @ExceptionHandler(Exception.class)
  public @Nullable ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
    LOG.error("Unhandled exception", ex);
    return problem(ex, HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR, request);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldViolation> violations = FieldViolation.of(ex.getBindingResult().getAllErrors());
    return validationProblem(ex, violations, headers, status, request);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return validationProblem(ex, FieldViolation.of(ex), headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> createResponseEntity(
      @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    if (body instanceof ProblemDetail problem) {
      // Spring's default details echo client input or internals (e.g. "No static resource x").
      problem.setDetail(null);
      @Nullable Map<String, Object> properties = problem.getProperties();
      if (properties == null || !properties.containsKey(CODE)) {
        problem.setProperty(CODE, genericCodeOf(statusCode));
      }
      if (request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
          instanceof String requestId) {
        problem.setProperty("requestId", requestId);
      }
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }

  private @Nullable ResponseEntity<Object> validationProblem(
      Exception ex,
      List<FieldViolation> violations,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setProperty(CODE, VALIDATION_FAILED);
    problem.setProperty("errors", violations);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  private @Nullable ResponseEntity<Object> problem(
      Exception ex, HttpStatus status, String code, WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setProperty(CODE, code);
    return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
  }

  /** Code derived from the status of an error raised by Spring, e.g. {@code http.not-found}. */
  private static String genericCodeOf(HttpStatusCode status) {
    HttpStatus known = HttpStatus.resolve(status.value());
    String name = known == null ? String.valueOf(status.value()) : known.name();
    return "http." + name.toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
