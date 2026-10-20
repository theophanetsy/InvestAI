package dev.investai.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.support.IntegrationTest;
import dev.investai.support.McpTestClient;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/**
 * Rate limit of {@code /mcp} through the real container (spec 3.0, criterion 10). Deliberately its
 * own Spring context (and database container), with a budget of {@value #BUDGET}: exhausting the
 * budgets of the shared context would turn the 401 of other tests into 429 for a minute.
 */
@IntegrationTest
@TestPropertySource(properties = "investai.mcp.requests-per-minute=" + McpRateLimitIT.BUDGET)
class McpRateLimitIT {

  /** Requests per minute in this context. */
  static final int BUDGET = 3;

  private static final String PING = "ping";
  private static final String NO_PARAMS = "{}";

  @LocalServerPort private int port;

  private McpTestClient mcp;

  @BeforeEach
  void setUp() {
    mcp = new McpTestClient(port);
  }

  @AfterEach
  void close() {
    mcp.close();
  }

  @Test
  void refusedRequestsAreLimitedWithoutBlockingTheClientThenTheClientToo() throws Exception {
    for (int i = 0; i < BUDGET; i++) {
      assertThat(mcp.post(mcp.request(PING, NO_PARAMS)).statusCode()).isEqualTo(401);
    }
    assertRateLimited(mcp.post(mcp.request(PING, NO_PARAMS)));

    // The legitimate client keeps its own budget...
    for (int i = 0; i < BUDGET; i++) {
      assertThat(mcp.call(PING, NO_PARAMS).statusCode()).isEqualTo(200);
    }
    // ...which is limited as well.
    assertRateLimited(mcp.call(PING, NO_PARAMS));
  }

  private static void assertRateLimited(HttpResponse<String> response) {
    assertThat(response.statusCode()).isEqualTo(429);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
    var problem = McpTestClient.json(response.body());
    assertThat(problem.get("code").asString()).isEqualTo(McpAccessFilter.RATE_LIMITED);
    assertThat(problem.get("requestId").asString())
        .isEqualTo(response.headers().firstValue("X-Request-Id").orElseThrow());
    assertThat(response.headers().firstValue("Retry-After"))
        .hasValueSatisfying(seconds -> assertThat(Integer.parseInt(seconds)).isBetween(1, 60));
  }
}
