package dev.investai.auth.infrastructure;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Security of the MCP endpoint (ADR-0010, spec 3.0, criteria 7 to 10), only when {@code
 * INVESTAI_MCP_TOKEN_HASH} is set. Its own filter chain, evaluated before the API's: dedicated
 * bearer token checked against its hash, {@code Origin} refused, rate limit, and no session at all
 * (the session cookie is never read, none is created). The token does not open {@code /api}.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(McpTokenConfigured.class)
public class McpSecurityConfiguration {

  private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
  private static final String BEARER_CHALLENGE = "Bearer realm=\"investai\"";

  /**
   * Security rules of {@code /mcp}.
   *
   * @param http the builder
   * @param exceptionResolver Spring MVC's resolvers, which render problems
   * @param endpoint path of the MCP endpoint
   * @param tokenHash SHA-256 of the MCP token
   * @param requestsPerMinute budget of the rate limit
   * @param clock current time
   * @return the filter chain
   */
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  SecurityFilterChain mcpSecurity(
      HttpSecurity http,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
      @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint}") String endpoint,
      @Value("${" + McpEndpointActivation.TOKEN_HASH + "}") String tokenHash,
      @Value("${investai.mcp.requests-per-minute}") int requestsPerMinute,
      Clock clock) {
    var problems = new ProblemSecurityHandler(exceptionResolver);
    var access =
        new McpAccessFilter(
            McpTokenVerifier.of(tokenHash),
            new SlidingWindowRateLimiter(requestsPerMinute, RATE_WINDOW, clock),
            new SlidingWindowRateLimiter(requestsPerMinute, RATE_WINDOW, clock),
            problems);
    http.securityMatcher(endpoint, endpoint + "/**")
        .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
        .addFilterBefore(access, ExceptionTranslationFilter.class)
        // Stateless: McpAccessFilter sets the authentication for this request only (nothing to
        // save), and the session repository of the API is replaced so that a session cookie is
        // never read here.
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .securityContext(
            context ->
                context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
        // No cookie authenticates this endpoint (bearer only), hence no CSRF token to check.
        // Ignored on the endpoint rather than disabled: SAST rules flag any disabled CSRF.
        .csrf(csrf -> csrf.ignoringRequestMatchers(endpoint, endpoint + "/**"))
        .requestCache(cache -> cache.disable())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, cause) -> {
                          // RFC 6750: tell the client which scheme to use.
                          response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
                          problems.commence(request, response, cause);
                        })
                    .accessDeniedHandler(problems))
        .headers(SecurityConfiguration::secureHeaders);
    return http.build();
  }
}
