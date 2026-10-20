package dev.investai.auth.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards {@code /mcp} (ADR-0010, spec 3.0, criteria 8 to 10):
 *
 * <ol>
 *   <li>rate limit, with separate budgets for authenticated and refused requests, so that a local
 *       process without the token cannot exhaust the budget of the legitimate client (429);
 *   <li>any {@code Origin} header is refused (403): MCP clients send none, browsers always do (DNS
 *       rebinding, cross-site requests);
 *   <li>the bearer token is checked against its hash; a valid one authenticates this request only
 *       (no session).
 * </ol>
 *
 * <p>A missing or wrong token is answered 401 by the authorisation filter, identically.
 */
final class McpAccessFilter extends OncePerRequestFilter {

  /** Error code of a rate-limited request. */
  static final String RATE_LIMITED = "mcp.rate-limited";

  /** Name of the authenticated principal: the MCP client holding the token. */
  static final String PRINCIPAL = "mcp-client";

  private static final Logger LOG = LoggerFactory.getLogger(McpAccessFilter.class);
  private static final String BEARER = "bearer ";

  private final McpTokenVerifier verifier;
  private final SlidingWindowRateLimiter authenticatedBudget;
  private final SlidingWindowRateLimiter rejectedBudget;
  private final ProblemSecurityHandler problems;
  private final SecurityContextHolderStrategy contexts =
      SecurityContextHolder.getContextHolderStrategy();

  McpAccessFilter(
      McpTokenVerifier verifier,
      SlidingWindowRateLimiter authenticatedBudget,
      SlidingWindowRateLimiter rejectedBudget,
      ProblemSecurityHandler problems) {
    this.verifier = verifier;
    this.authenticatedBudget = authenticatedBudget;
    this.rejectedBudget = rejectedBudget;
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    HttpHeaders headers = new ServletServerHttpRequest(request).getHeaders();
    boolean fromBrowser = headers.getOrigin() != null;
    boolean authenticated = !fromBrowser && bearer(headers).map(verifier::matches).orElse(false);
    // Refused requests, Origin included, share one budget: they cannot flood the logs either.
    SlidingWindowRateLimiter.Decision decision =
        (authenticated ? authenticatedBudget : rejectedBudget).tryAcquire();
    if (!decision.allowed()) {
      response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
      problems.reject(request, response, HttpStatus.TOO_MANY_REQUESTS, RATE_LIMITED);
      return;
    }
    if (fromBrowser) {
      // Never log the value: it is client input.
      LOG.info("MCP request refused: Origin header present");
      problems.handle(request, response, new AccessDeniedException("Origin header on /mcp"));
      return;
    }
    if (authenticated) {
      SecurityContext context = contexts.createEmptyContext();
      context.setAuthentication(
          UsernamePasswordAuthenticationToken.authenticated(PRINCIPAL, null, List.of()));
      contexts.setContext(context);
    }
    chain.doFilter(request, response);
  }

  /** The credentials of the {@code Authorization} header, if it uses the bearer scheme. */
  private static Optional<String> bearer(HttpHeaders headers) {
    return Optional.ofNullable(headers.getFirst(HttpHeaders.AUTHORIZATION))
        .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(BEARER))
        .map(value -> value.substring(BEARER.length()));
  }
}
