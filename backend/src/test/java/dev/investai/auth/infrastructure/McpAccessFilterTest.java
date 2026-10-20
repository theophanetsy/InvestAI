package dev.investai.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.investai.support.FixtureCredentials;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

class McpAccessFilterTest {

  private static final int BUDGET = 3;
  private static final String VALID = "Bearer " + FixtureCredentials.MCP_BEARER;

  private final HandlerExceptionResolver resolver = mock();
  private final AtomicReference<@Nullable Authentication> seenByChain = new AtomicReference<>();
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private McpAccessFilter filter;
  private int chainCalls;

  @BeforeEach
  void setUp() {
    given(resolver.resolveException(any(), any(), isNull(), any())).willReturn(new ModelAndView());
    Clock clock = Clock.systemUTC();
    filter =
        new McpAccessFilter(
            McpTokenVerifier.of(FixtureCredentials.MCP_HASH),
            new SlidingWindowRateLimiter(BUDGET, Duration.ofMinutes(1), clock),
            new SlidingWindowRateLimiter(BUDGET, Duration.ofMinutes(1), clock),
            new ProblemSecurityHandler(resolver));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void validBearerAuthenticatesTheRequest() throws Exception {
    run(request(VALID));

    assertThat(chainCalls).isEqualTo(1);
    Authentication authentication = Objects.requireNonNull(seenByChain.get());
    assertThat(authentication.isAuthenticated()).isTrue();
    assertThat(authentication.getName()).isEqualTo(McpAccessFilter.PRINCIPAL);
  }

  @Test
  void schemeIsCaseInsensitive() throws Exception {
    run(request("bearer " + FixtureCredentials.MCP_BEARER));

    assertThat(seenByChain.get()).isNotNull();
  }

  @Test
  void missingWrongOrNonBearerCredentialsLeaveTheRequestAnonymous() throws Exception {
    run(request(null));
    assertThat(seenByChain.get()).isNull();
    run(request(VALID + "x"));
    assertThat(seenByChain.get()).isNull();
    run(request(FixtureCredentials.MCP_BEARER));
    assertThat(seenByChain.get()).isNull();

    // The authorisation filter answers 401 further down the chain.
    assertThat(chainCalls).isEqualTo(3);
  }

  @Test
  void anyOriginIsForbiddenEvenWithTheToken() throws Exception {
    var request = request(VALID);
    request.addHeader("Origin", "http://localhost:8080");

    run(request);

    assertThat(chainCalls).isZero();
    var problem = resolvedProblem();
    assertThat(problem.getStatusCode().value()).isEqualTo(403);
    assertThat(problem.getBody().getProperties()).containsEntry("code", "auth.forbidden");
  }

  @Test
  void originRefusalsAreRateLimitedLikeAnyRefusedRequest() throws Exception {
    for (int i = 0; i < BUDGET; i++) {
      var fromBrowser = request(VALID);
      fromBrowser.addHeader("Origin", "https://evil.example");
      run(fromBrowser);
    }
    var fromBrowser = request(VALID);
    fromBrowser.addHeader("Origin", "https://evil.example");

    run(fromBrowser);

    assertThat(chainCalls).isZero();
    assertThat(response.getHeader("Retry-After")).isNotNull();
  }

  @Test
  void exhaustedBudgetAnswers429WithRetryAfter() throws Exception {
    for (int i = 0; i < BUDGET; i++) {
      run(request(VALID));
    }

    run(request(VALID));

    assertThat(chainCalls).isEqualTo(BUDGET);
    var problem = resolvedProblem();
    assertThat(problem.getStatusCode().value()).isEqualTo(429);
    assertThat(problem.getBody().getProperties())
        .containsEntry("code", McpAccessFilter.RATE_LIMITED);
    assertThat(response.getHeader("Retry-After")).matches("[1-9][0-9]*");
  }

  @Test
  void rejectedRequestsCannotExhaustTheBudgetOfTheClient() throws Exception {
    for (int i = 0; i < BUDGET + 2; i++) {
      run(request(null));
    }
    assertThat(chainCalls).isEqualTo(BUDGET);

    run(request(VALID));

    assertThat(chainCalls).isEqualTo(BUDGET + 1);
    assertThat(seenByChain.get()).isNotNull();
  }

  private static MockHttpServletRequest request(@Nullable String authorization) {
    var request = new MockHttpServletRequest("POST", "/mcp");
    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }
    return request;
  }

  private void run(MockHttpServletRequest request) throws IOException, ServletException {
    seenByChain.set(null);
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          chainCalls++;
          seenByChain.set(SecurityContextHolder.getContext().getAuthentication());
        });
    SecurityContextHolder.clearContext();
  }

  private ErrorResponseException resolvedProblem() {
    var captor = ArgumentCaptor.forClass(Exception.class);
    verify(resolver).resolveException(any(), any(), isNull(), captor.capture());
    return (ErrorResponseException) captor.getValue();
  }
}
