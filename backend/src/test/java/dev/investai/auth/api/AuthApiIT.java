package dev.investai.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.support.FixtureCredentials;
import dev.investai.support.IntegrationTest;
import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Authentication flow through the real servlet container, as a browser (Angular) would see it:
 * cookies, CSRF double submit, session renewal, errors raised outside Spring MVC (ADR-0007).
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AuthApiIT {

  private static final String LOGIN = "/api/v1/auth/login";
  private static final String LOGOUT = "/api/v1/auth/logout";
  private static final String ME = "/api/v1/auth/me";
  private static final String STATUS = "/api/v1/status";
  private static final String SESSION_COOKIE = "JSESSIONID";
  private static final String CSRF_COOKIE = "XSRF-TOKEN";
  private static final String CSRF_HEADER = "X-XSRF-TOKEN";
  private static final String PROBLEM_JSON = "application/problem+json";

  private final String badPassword = UUID.randomUUID().toString();
  private final HttpClient http = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @AfterEach
  void closeClient() {
    http.close();
  }

  @Test
  void anonymousRequestIsRejectedWithProblemAndSecurityHeaders() throws Exception {
    var browser = new Browser();

    var response = browser.get(ME);

    assertThat(response.statusCode()).isEqualTo(401);
    assertProblem(response, "auth.required");
    assertSecurityHeaders(response);
    assertThat(browser.cookies).doesNotContainKey(SESSION_COOKIE);
  }

  @Test
  void everyEndpointIsClosedByDefault() throws Exception {
    var response = new Browser().get("/api/v1/portfolios");

    assertThat(response.statusCode()).isEqualTo(401);
    assertProblem(response, "auth.required");
  }

  @Test
  void loginOpensSessionUntilLogout() throws Exception {
    var browser = new Browser();
    browser.get(STATUS);
    String anonymousCsrfToken = browser.cookies.get(CSRF_COOKIE);

    var login = browser.login(FixtureCredentials.USERNAME, FixtureCredentials.PASSPHRASE);

    assertThat(login.statusCode()).isEqualTo(204);
    assertThat(setCookie(login, SESSION_COOKIE))
        .contains("HttpOnly")
        .contains("SameSite=Strict")
        .contains("Secure");
    assertThat(browser.cookies.get(CSRF_COOKIE)).isNotBlank().isNotEqualTo(anonymousCsrfToken);
    var me = browser.get(ME);
    assertThat(me.statusCode()).isEqualTo(200);
    assertThat(me.body()).isEqualTo("{\"username\":\"" + FixtureCredentials.USERNAME + "\"}");
    assertSecurityHeaders(me);
    var copy = browser.copy();

    var logout = browser.post(LOGOUT, "");

    assertThat(logout.statusCode()).isEqualTo(204);
    assertThat(setCookie(logout, SESSION_COOKIE)).startsWith(SESSION_COOKIE + "=;");
    assertThat(browser.get(ME).statusCode()).isEqualTo(401);
    // The session is invalidated on the server, not only forgotten by the browser.
    assertThat(copy.get(ME).statusCode()).isEqualTo(401);
  }

  @Test
  void loginRenewsTheSessionIdentifier() throws Exception {
    var browser = new Browser();
    browser.get(STATUS);
    browser.login(FixtureCredentials.USERNAME, FixtureCredentials.PASSPHRASE);
    String firstSession = browser.cookies.get(SESSION_COOKIE);
    var stolen = browser.copy();

    browser.login(FixtureCredentials.USERNAME, FixtureCredentials.PASSPHRASE);

    assertThat(browser.cookies.get(SESSION_COOKIE)).isNotBlank().isNotEqualTo(firstSession);
    assertThat(browser.get(ME).statusCode()).isEqualTo(200);
    assertThat(stolen.get(ME).statusCode()).isEqualTo(401);
  }

  @Test
  void wrongPasswordAndUnknownUserGetTheSameAnswer(CapturedOutput output) throws Exception {
    var browser = new Browser();
    browser.get(STATUS);

    var wrongPassword = browser.login(FixtureCredentials.USERNAME, badPassword);
    var unknownUser = browser.login("someone-else", badPassword);

    assertThat(wrongPassword.statusCode()).isEqualTo(401);
    assertThat(unknownUser.statusCode()).isEqualTo(401);
    assertProblem(wrongPassword, "auth.invalid-credentials");
    assertProblem(unknownUser, "auth.invalid-credentials");
    assertThat(withoutRequestId(unknownUser.body()))
        .isEqualTo(withoutRequestId(wrongPassword.body()));
    assertThat(browser.cookies).doesNotContainKey(SESSION_COOKIE);
    assertThat(output.getOut().lines())
        .filteredOn(line -> line.contains("Login failed"))
        .hasSize(2)
        .allSatisfy(
            line ->
                assertThat(line)
                    .contains("\"level\":\"WARN\"")
                    .doesNotContain(FixtureCredentials.USERNAME)
                    .doesNotContain("someone-else")
                    .doesNotContain(badPassword));
  }

  @Test
  void modifyingRequestWithoutCsrfTokenIsRejected() throws Exception {
    var browser = new Browser();
    browser.get(STATUS);

    var response = browser.send(post(LOGIN, credentials()).build());

    assertThat(response.statusCode()).isEqualTo(403);
    assertProblem(response, "auth.csrf-invalid");
  }

  @Test
  void modifyingRequestWithWrongCsrfTokenIsRejected() throws Exception {
    var browser = new Browser();
    browser.get(STATUS);

    var response =
        browser.send(post(LOGIN, credentials()).header(CSRF_HEADER, "not-the-token").build());

    assertThat(response.statusCode()).isEqualTo(403);
    assertProblem(response, "auth.csrf-invalid");
  }

  @Test
  void logoutWithoutCsrfHeaderKeepsTheSession() throws Exception {
    var browser = loggedIn();
    // Readable by any page served on another localhost port: only the header must count.
    String token = Objects.requireNonNull(browser.cookies.get(CSRF_COOKIE));
    String maskedWithZeros = xorMaskedWithZeros(token);

    var withoutToken = browser.send(post(LOGOUT, "").build());
    var tokenAsParameter = browser.send(post(LOGOUT + "?_csrf=" + token, "").build());
    var maskedTokenAsParameter =
        browser.send(post(LOGOUT + "?_csrf=" + maskedWithZeros, "").build());

    assertThat(withoutToken.statusCode()).isEqualTo(403);
    assertThat(tokenAsParameter.statusCode()).isEqualTo(403);
    assertThat(maskedTokenAsParameter.statusCode()).isEqualTo(403);
    assertProblem(maskedTokenAsParameter, "auth.csrf-invalid");
    assertThat(browser.get(ME).statusCode()).isEqualTo(200);
  }

  @Test
  void requestRejectedByTheHttpFirewallIsProblem() throws Exception {
    var response = new Browser().get(STATUS + ";param=value");

    assertThat(response.statusCode()).isEqualTo(400);
    assertProblem(response, "http.bad-request");
  }

  private Browser loggedIn() throws IOException, InterruptedException {
    var browser = new Browser();
    browser.get(STATUS);
    assertThat(
            browser.login(FixtureCredentials.USERNAME, FixtureCredentials.PASSPHRASE).statusCode())
        .isEqualTo(204);
    return browser;
  }

  /** What Spring Security's XOR handler would accept: random bytes set to zero, then the token. */
  private static String xorMaskedWithZeros(String token) {
    byte[] raw = token.getBytes(StandardCharsets.UTF_8);
    byte[] masked = new byte[raw.length * 2];
    System.arraycopy(raw, 0, masked, raw.length, raw.length);
    return Base64.getUrlEncoder().encodeToString(masked);
  }

  private static void assertSecurityHeaders(HttpResponse<String> response) {
    assertThat(header(response, "Cache-Control")).contains("no-store");
    assertThat(header(response, "X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(header(response, "X-Frame-Options")).isEqualTo("DENY");
    assertThat(header(response, "Content-Security-Policy"))
        .isEqualTo("default-src 'none'; frame-ancestors 'none'");
  }

  private static void assertProblem(HttpResponse<String> response, String code) {
    assertThat(header(response, "Content-Type")).startsWith(PROBLEM_JSON);
    assertThat(response.body())
        .contains("\"code\":\"" + code + "\"")
        .contains("\"requestId\":\"" + header(response, "X-Request-Id") + "\"")
        .doesNotContain("\"detail\"");
  }

  private static String withoutRequestId(String body) {
    return body.replaceAll("\"requestId\":\"[^\"]+\"", "");
  }

  private static String header(HttpResponse<String> response, String name) {
    return response.headers().firstValue(name).orElse("");
  }

  private static String setCookie(HttpResponse<String> response, String name) {
    return response.headers().allValues("Set-Cookie").stream()
        .filter(cookie -> cookie.startsWith(name + "="))
        .reduce((first, last) -> last)
        .orElse("");
  }

  private static String credentials() {
    return credentials(FixtureCredentials.USERNAME, FixtureCredentials.PASSPHRASE);
  }

  private static String credentials(String username, String password) {
    return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
  }

  private HttpRequest.Builder post(String path, String json) {
    return HttpRequest.newBuilder(uri(path))
        .header("Content-Type", "application/json")
        .POST(BodyPublishers.ofString(json));
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  /** Minimal browser: keeps cookies and echoes the CSRF cookie in a header, like Angular. */
  private final class Browser {

    private final Map<String, String> cookies = new LinkedHashMap<>();

    HttpResponse<String> get(String path) throws IOException, InterruptedException {
      return send(HttpRequest.newBuilder(uri(path)).GET().build());
    }

    HttpResponse<String> post(String path, String json) throws IOException, InterruptedException {
      return send(
          AuthApiIT.this.post(path, json).header(CSRF_HEADER, cookies.get(CSRF_COOKIE)).build());
    }

    HttpResponse<String> login(String username, String password)
        throws IOException, InterruptedException {
      return post(LOGIN, credentials(username, password));
    }

    HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
      HttpRequest withCookies = request;
      if (!cookies.isEmpty()) {
        withCookies =
            HttpRequest.newBuilder(request, (name, value) -> true)
                .header("Cookie", cookieHeader())
                .build();
      }
      var response = http.send(withCookies, BodyHandlers.ofString());
      for (String header : response.headers().allValues("Set-Cookie")) {
        store(HttpCookie.parse(header));
      }
      return response;
    }

    private void store(List<HttpCookie> received) {
      for (HttpCookie cookie : received) {
        if (cookie.getMaxAge() == 0 || cookie.getValue().isEmpty()) {
          cookies.remove(cookie.getName());
        } else {
          cookies.put(cookie.getName(), cookie.getValue());
        }
      }
    }

    Browser copy() {
      var copy = new Browser();
      copy.cookies.putAll(cookies);
      return copy;
    }

    private String cookieHeader() {
      return cookies.entrySet().stream()
          .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
          .collect(Collectors.joining("; "));
    }
  }
}
