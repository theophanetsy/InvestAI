package dev.investai.auth.infrastructure;

import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Single-user authentication (ADR-0007): closed by default, server-side session in an {@code
 * HttpOnly} cookie, CSRF double submit for Angular ({@code XSRF-TOKEN} cookie, {@code X-XSRF-TOKEN}
 * header), errors as problem details. Login is a JSON endpoint ({@code AuthController}); there is
 * no form login nor HTTP Basic.
 */
@Configuration(proxyBeanMethods = false)
@Import({SingleUserConfiguration.class, HttpFirewallConfiguration.class})
public class SecurityConfiguration {

  /** Servlet container default; keep in sync if server.servlet.session.cookie.name is set. */
  private static final String SESSION_COOKIE = "JSESSIONID";

  private static final String CONTENT_SECURITY_POLICY =
      "default-src 'none'; frame-ancestors 'none'";

  /**
   * Security rules of the HTTP API.
   *
   * @param http the builder
   * @param exceptionResolver Spring MVC's resolvers, which render problems
   * @param csrfTokens where CSRF tokens live
   * @param securityContexts where authenticated sessions live
   * @return the filter chain
   */
  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
      CsrfTokenRepository csrfTokens,
      SecurityContextRepository securityContexts) {
    var problems = new ProblemSecurityHandler(exceptionResolver);
    http.authorizeHttpRequests(SecurityConfiguration::closedByDefault)
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokens)
                    .csrfTokenRequestHandler(new HeaderOnlyCsrfTokenRequestHandler()))
        .securityContext(context -> context.securityContextRepository(securityContexts))
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
        // A 401 must not create a session to remember the request.
        .requestCache(cache -> cache.disable())
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/v1/auth/logout")
                    .logoutSuccessHandler(
                        new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                    .deleteCookies(SESSION_COOKIE))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)));
    return http.build();
  }

  /** Public endpoints are listed explicitly; everything else requires a session. */
  private static void closedByDefault(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          requests) {
    requests
        // Errors forwarded by the container are rendered for the original request.
        .dispatcherTypeMatchers(DispatcherType.ERROR)
        .permitAll()
        .requestMatchers(
            HttpMethod.GET,
            "/api/v1/status",
            "/actuator/health/liveness",
            "/actuator/health/readiness")
        .permitAll()
        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
        .permitAll()
        .anyRequest()
        .authenticated();
  }

  /**
   * CSRF tokens in a cookie readable by Angular, never sent cross-site.
   *
   * @return the repository
   */
  @Bean
  CsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));
    return repository;
  }

  /**
   * Authenticated security contexts are kept in the HTTP session.
   *
   * @return the repository
   */
  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  /**
   * What a successful login does to the session: new session identifier (fixation) and new CSRF
   * token, sent right away so that Angular can keep going.
   *
   * @param csrfTokens where CSRF tokens live
   * @return the strategy
   */
  @Bean
  SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokens) {
    var csrfRotation = new CsrfAuthenticationStrategy(csrfTokens);
    csrfRotation.setRequestHandler(new HeaderOnlyCsrfTokenRequestHandler());
    return new CompositeSessionAuthenticationStrategy(
        List.of(new ChangeSessionIdAuthenticationStrategy(), csrfRotation));
  }
}
