package dev.investai.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

class ProblemSecurityHandlerTest {

  private final HandlerExceptionResolver resolver = mock();
  private final ProblemSecurityHandler handler = new ProblemSecurityHandler(resolver);
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @Test
  void missingAuthenticationIsDelegatedAsAuthRequired() throws Exception {
    given(resolver.resolveException(any(), any(), isNull(), any())).willReturn(new ModelAndView());

    handler.commence(request, response, new InsufficientAuthenticationException("anonymous"));

    assertThat(resolvedProblem().getBody().getProperties()).containsEntry("code", "auth.required");
    assertThat(resolvedProblem().getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void csrfFailureIsDistinguishedFromOtherDenials() throws Exception {
    given(resolver.resolveException(any(), any(), isNull(), any())).willReturn(new ModelAndView());

    handler.handle(request, response, new MissingCsrfTokenException(null));

    assertThat(resolvedProblem().getBody().getProperties())
        .containsEntry("code", "auth.csrf-invalid");
    assertThat(resolvedProblem().getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void otherDenialIsForbidden() throws Exception {
    given(resolver.resolveException(any(), any(), isNull(), any())).willReturn(new ModelAndView());

    handler.handle(request, response, new AccessDeniedException("denied"));

    assertThat(resolvedProblem().getBody().getProperties()).containsEntry("code", "auth.forbidden");
    assertThat(resolvedProblem().getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void fallsBackToErrorPageWhenNothingResolvesTheProblem() throws Exception {
    handler.commence(request, response, new InsufficientAuthenticationException("anonymous"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.isCommitted()).isTrue();
  }

  private ErrorResponseException resolvedProblem() {
    var captor = ArgumentCaptor.forClass(Exception.class);
    verify(resolver).resolveException(any(), any(), isNull(), captor.capture());
    assertThat(captor.getValue()).isInstanceOf(ErrorResponseException.class);
    return (ErrorResponseException) captor.getValue();
  }
}
