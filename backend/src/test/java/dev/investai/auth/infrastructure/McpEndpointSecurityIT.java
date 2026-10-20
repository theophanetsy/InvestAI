package dev.investai.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.support.FixtureCredentials;
import dev.investai.support.IntegrationTest;
import dev.investai.support.McpTestClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpCookie;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Security of {@code /mcp} through the real servlet container (spec 3.0, criteria 8 to 11). The
 * rate limit has its own context: {@code McpRateLimitIT}.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class McpEndpointSecurityIT {

  private static final String PING = "ping";
  private static final String NO_PARAMS = "{}";
  private static final String AUTHORIZATION = "Authorization";
  private static final String PROBLEM_JSON = "application/problem+json";

  private final HttpClient http = HttpClient.newHttpClient();
  private McpTestClient mcp;

  @LocalServerPort private int port;

  @BeforeEach
  void setUp() {
    mcp = new McpTestClient(port);
  }

  @AfterEach
  void close() {
    mcp.close();
    http.close();
  }

  @Test
  void validTokenIsServedWithoutAnySession() throws Exception {
    var response = mcp.call(PING, NO_PARAMS);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(McpTestClient.json(response.body()).get("result").isObject()).isTrue();
    assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
  }

  @Test
  void missingAndWrongTokensGetTheSameUnauthorizedProblem(CapturedOutput output) throws Exception {
    String wrongBearer = "investai_mcp_" + UUID.randomUUID();

    var missing = mcp.post(mcp.request(PING, NO_PARAMS));
    var wrong = mcp.post(mcp.request(PING, NO_PARAMS), AUTHORIZATION, "Bearer " + wrongBearer);

    assertThat(missing.statusCode()).isEqualTo(401);
    assertThat(wrong.statusCode()).isEqualTo(401);
    assertProblem(missing, "auth.required");
    assertProblem(wrong, "auth.required");
    assertThat(withoutRequestId(wrong.body())).isEqualTo(withoutRequestId(missing.body()));
    assertThat(wrong.headers().allValues("Set-Cookie")).isEmpty();
    assertThat(missing.headers().firstValue("WWW-Authenticate"))
        .hasValue("Bearer realm=\"investai\"");
    assertThat(output.getAll())
        .doesNotContain(wrongBearer)
        .doesNotContain(FixtureCredentials.MCP_BEARER);
  }

  @Test
  void anyOriginIsForbiddenEvenWithTheToken() throws Exception {
    for (String origin : List.of("http://localhost:" + port, "https://evil.example", "null")) {
      var response =
          mcp.post(
              mcp.request(PING, NO_PARAMS),
              AUTHORIZATION,
              McpTestClient.VALID_AUTHORIZATION,
              "Origin",
              origin);

      assertThat(response.statusCode()).isEqualTo(403);
      assertProblem(response, "auth.forbidden");
    }
  }

  @Test
  void sessionCookieDoesNotAuthenticateTheMcpEndpoint() throws Exception {
    String session = login();

    var mcpWithSession = mcp.post(mcp.request(PING, NO_PARAMS), "Cookie", session);

    assertThat(mcpWithSession.statusCode()).isEqualTo(401);
    assertProblem(mcpWithSession, "auth.required");
    // The session itself is valid: it opens the API.
    assertThat(get("/api/v1/auth/me", "Cookie", session).statusCode()).isEqualTo(200);
  }

  @Test
  void mcpTokenDoesNotOpenTheApi() throws Exception {
    var response = get("/api/v1/auth/me", AUTHORIZATION, McpTestClient.VALID_AUTHORIZATION);

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void unexpectedHostIsRejectedByTheFirewall() throws Exception {
    // The JDK HTTP client cannot set Host (DNS rebinding): raw request on a socket.
    String request =
        String.join(
            "\r\n",
            "POST /mcp HTTP/1.1",
            "Host: rebound.example:" + port,
            AUTHORIZATION + ": " + McpTestClient.VALID_AUTHORIZATION,
            "Content-Type: application/json",
            "Accept: application/json, text/event-stream",
            "Content-Length: 2",
            "Connection: close",
            "",
            NO_PARAMS);
    String statusLine;
    try (var socket = new Socket("localhost", port);
        var reader =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
      socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
      statusLine = reader.readLine();
    }

    assertThat(statusLine).startsWith("HTTP/1.1 400");
  }

  private String login() throws IOException, InterruptedException {
    var status = get("/api/v1/status");
    String csrf = cookie(status, "XSRF-TOKEN");
    var login =
        http.send(
            HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + csrf)
                .header("X-XSRF-TOKEN", csrf)
                .POST(
                    BodyPublishers.ofString(
                        """
                        {"username":"%s","password":"%s"}\
                        """
                            .formatted(FixtureCredentials.USERNAME, FixtureCredentials.PASSPHRASE)))
                .build(),
            BodyHandlers.ofString());
    assertThat(login.statusCode()).isEqualTo(204);
    return "JSESSIONID=" + cookie(login, "JSESSIONID");
  }

  private HttpResponse<String> get(String path, String... headers)
      throws IOException, InterruptedException {
    var builder = HttpRequest.newBuilder(uri(path)).GET();
    if (headers.length > 0) {
      builder.headers(headers);
    }
    return http.send(builder.build(), BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private static String cookie(HttpResponse<String> response, String name) {
    return response.headers().allValues("Set-Cookie").stream()
        .flatMap(header -> HttpCookie.parse(header).stream())
        .filter(cookie -> cookie.getName().equals(name))
        .map(HttpCookie::getValue)
        .findFirst()
        .orElseThrow();
  }

  private static void assertProblem(HttpResponse<String> response, String code) {
    assertThat(response.headers().firstValue("Content-Type")).hasValue(PROBLEM_JSON);
    var problem = McpTestClient.json(response.body());
    assertThat(problem.get("code").asString()).isEqualTo(code);
    assertThat(problem.get("requestId").asString())
        .isEqualTo(response.headers().firstValue("X-Request-Id").orElseThrow());
  }

  private static String withoutRequestId(String body) {
    return body.replaceAll("\"requestId\":\"[^\"]*\"", "");
  }
}
