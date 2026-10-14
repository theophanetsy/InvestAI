package dev.investai.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class HeaderOnlyCsrfTokenRequestHandlerTest {

  private static final String TOKEN_VALUE = "token-value";

  private final HeaderOnlyCsrfTokenRequestHandler handler = new HeaderOnlyCsrfTokenRequestHandler();
  private final CsrfToken token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", TOKEN_VALUE);
  private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/x");

  @Test
  void readsTheHeader() {
    request.addHeader("X-XSRF-TOKEN", TOKEN_VALUE);

    assertThat(handler.resolveCsrfTokenValue(request, token)).isEqualTo(TOKEN_VALUE);
  }

  @Test
  void ignoresTheParameter() {
    request.addParameter("_csrf", TOKEN_VALUE);

    assertThat(handler.resolveCsrfTokenValue(request, token)).isNull();
  }

  @Test
  void exposesTheTokenToTheRequestEagerly() {
    var loaded = new boolean[1];

    handler.handle(
        request,
        new MockHttpServletResponse(),
        () -> {
          loaded[0] = true;
          return token;
        });

    assertThat(loaded[0]).isTrue();
    assertThat(request.getAttribute(CsrfToken.class.getName())).isNotNull();
  }
}
