package dev.investai.auth.api;

import dev.investai.shared.api.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON login for the Angular frontend (ADR-0007). Logout is handled by Spring Security ({@code POST
 * /api/v1/auth/logout}).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);
  private static final String INVALID_CREDENTIALS = "auth.invalid-credentials";

  private final AuthenticationManager authenticationManager;
  private final SessionAuthenticationStrategy sessionStrategy;
  private final SecurityContextRepository securityContexts;

  /**
   * Creates the controller.
   *
   * @param authenticationManager checks the credentials
   * @param sessionStrategy renews the session and the CSRF token on login
   * @param securityContexts stores the authenticated session
   */
  public AuthController(
      AuthenticationManager authenticationManager,
      SessionAuthenticationStrategy sessionStrategy,
      SecurityContextRepository securityContexts) {
    this.authenticationManager = authenticationManager;
    this.sessionStrategy = sessionStrategy;
    this.securityContexts = securityContexts;
  }

  /**
   * Opens an authenticated session. Wrong password and unknown user get the same answer.
   *
   * @param credentials the login form
   * @param request the current request
   * @param response the current response, receiving the session cookie
   */
  @PostMapping("/login")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void login(
      @Valid @RequestBody LoginRequest credentials,
      HttpServletRequest request,
      HttpServletResponse response) {
    Authentication authentication = authenticate(credentials);
    sessionStrategy.onAuthentication(authentication, request, response);
    // Saved in the session: the following requests are authenticated.
    securityContexts.saveContext(new SecurityContextImpl(authentication), request, response);
    LOG.info("Login succeeded");
  }

  /**
   * Returns the connected user; 401 when anonymous.
   *
   * @param authentication the authenticated user
   * @return the user
   */
  @GetMapping("/me")
  public CurrentUserResponse me(Authentication authentication) {
    return new CurrentUserResponse(authentication.getName());
  }

  private Authentication authenticate(LoginRequest credentials) {
    try {
      return authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(
              credentials.username(), credentials.password()));
    } catch (AuthenticationException ex) {
      // Neither the submitted username nor the password is logged.
      String reason = ex.getClass().getSimpleName();
      LOG.warn("Login failed: {}", reason);
      throw new ErrorResponseException(
          HttpStatus.UNAUTHORIZED,
          Problems.withCode(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS),
          ex);
    }
  }
}
