package dev.investai.auth.infrastructure;

import dev.investai.shared.api.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Answers 401/403 raised by the security filters with the problem details of ADR-0006. The problem
 * goes through Spring MVC's exception resolvers, hence through {@code ApiExceptionHandler}: one
 * place adds the {@code requestId} and serialises the body.
 */
final class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  static final String AUTH_REQUIRED = "auth.required";
  static final String AUTH_FORBIDDEN = "auth.forbidden";
  static final String CSRF_INVALID = "auth.csrf-invalid";

  private final HandlerExceptionResolver resolver;

  ProblemSecurityHandler(HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException cause)
      throws IOException {
    respond(request, response, HttpStatus.UNAUTHORIZED, AUTH_REQUIRED, cause);
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException cause)
      throws IOException {
    String code = cause instanceof CsrfException ? CSRF_INVALID : AUTH_FORBIDDEN;
    respond(request, response, HttpStatus.FORBIDDEN, code, cause);
  }

  private void respond(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String code,
      RuntimeException cause)
      throws IOException {
    var problem = new ErrorResponseException(status, Problems.withCode(status, code), cause);
    if (resolver.resolveException(request, response, null, problem) == null) {
      // Not expected: the container's error page (/error) still answers with a problem.
      response.sendError(status.value());
    }
  }
}
