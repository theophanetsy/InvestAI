package dev.investai.shared.api;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's {@code BasicErrorController}: errors raised outside Spring MVC (servlet
 * filters, HTTP firewall, container) are forwarded to {@code /error} with the original HTTP method
 * and answered with the same problem details as everything else (ADR-0006), through {@link
 * ApiExceptionHandler}. Nothing here changes state.
 */
@RestController
public class ErrorPageController implements ErrorController {

  private static final String ERROR_PATH = "${spring.web.error.path:/error}";

  // Two mappings instead of one for every method: a handler reachable by both safe and modifying
  // methods is flagged by FindSecBugs (SPRING_CSRF_UNRESTRICTED_REQUEST_MAPPING).

  /**
   * Error of a safe request (no CSRF token involved).
   *
   * @param request the error dispatch, carrying the original status
   */
  @RequestMapping(
      path = ERROR_PATH,
      method = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS})
  public void safeRequestError(HttpServletRequest request) {
    throw problemFor(request);
  }

  /**
   * Error of a modifying request.
   *
   * @param request the error dispatch, carrying the original status
   */
  @RequestMapping(
      path = ERROR_PATH,
      method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
  public void modifyingRequestError(HttpServletRequest request) {
    throw problemFor(request);
  }

  /** Server errors never reveal more than their code. */
  private static ErrorResponseException problemFor(HttpServletRequest request) {
    HttpStatusCode status =
        request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
            ? HttpStatusCode.valueOf(code)
            : HttpStatus.INTERNAL_SERVER_ERROR;
    ProblemDetail problem =
        status.is5xxServerError()
            ? Problems.withCode(status, ApiExceptionHandler.INTERNAL_ERROR)
            : ProblemDetail.forStatus(status);
    return new ErrorResponseException(status, problem, null);
  }
}
