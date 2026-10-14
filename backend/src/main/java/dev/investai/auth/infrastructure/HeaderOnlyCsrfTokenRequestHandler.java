package dev.investai.auth.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.jspecify.annotations.Nullable;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * CSRF double submit for a single-page app: the token is only read from the {@code X-XSRF-TOKEN}
 * header, never from a {@code _csrf} parameter. Cookies are not isolated by port, so a page served
 * by another local server ({@code localhost:5173}…) can read {@code XSRF-TOKEN}; it can submit a
 * form carrying it as a parameter, but cannot add a custom header to a cross-origin request.
 *
 * <p>The token is loaded on every request, which issues the cookie as soon as it is missing:
 * Angular needs it before its first modifying request. No BREACH masking: the token already travels
 * in clear in the cookie.
 */
final class HeaderOnlyCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

  HeaderOnlyCsrfTokenRequestHandler() {
    // Without a dedicated attribute name, the handler reads the token's parameter name, which
    // loads the deferred token, hence writes the cookie.
    setCsrfRequestAttributeName(null);
  }

  @Override
  public @Nullable String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    // Spring's resolution (header, then parameter) with the parameter channel removed.
    return super.resolveCsrfTokenValue(
        new WithoutParameter(request, csrfToken.getParameterName()), csrfToken);
  }

  /** Hides one request parameter. */
  private static final class WithoutParameter extends HttpServletRequestWrapper {

    private final String hidden;

    WithoutParameter(HttpServletRequest request, String hidden) {
      super(request);
      this.hidden = hidden;
    }

    @Override
    public @Nullable String getParameter(String name) {
      return hidden.equals(name) ? null : super.getParameter(name);
    }
  }
}
