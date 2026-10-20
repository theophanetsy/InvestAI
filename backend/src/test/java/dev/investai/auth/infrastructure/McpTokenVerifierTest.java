package dev.investai.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.investai.support.FixtureCredentials;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class McpTokenVerifierTest {

  private final McpTokenVerifier verifier = McpTokenVerifier.of(FixtureCredentials.MCP_HASH);

  @Test
  void acceptsTheTokenWhoseHashWasProducedByTheScript() {
    assertThat(verifier.matches(FixtureCredentials.MCP_BEARER)).isTrue();
  }

  @Test
  void rejectsAnyOtherToken() {
    assertThat(verifier.matches(FixtureCredentials.MCP_BEARER + "x")).isFalse();
    assertThat(verifier.matches(FixtureCredentials.MCP_BEARER.toUpperCase(java.util.Locale.ROOT)))
        .isFalse();
    assertThat(verifier.matches("")).isFalse();
    // The hash itself is not a token.
    assertThat(verifier.matches(FixtureCredentials.MCP_HASH)).isFalse();
  }

  @Test
  void rejectsOverlongTokensWithoutHashingThem() {
    assertThat(verifier.matches("a".repeat(McpTokenVerifier.MAX_TOKEN_LENGTH + 1))).isFalse();
  }

  @Test
  void refusesAMalformedHashWithoutEchoingIt() {
    // The token pasted instead of its hash, an uppercase hash, a truncated one.
    String pastedBearer = "investai_mcp_" + UUID.randomUUID();
    for (String malformed :
        new String[] {
          pastedBearer,
          FixtureCredentials.MCP_HASH.toUpperCase(java.util.Locale.ROOT),
          FixtureCredentials.MCP_HASH.substring(1)
        }) {
      assertThatThrownBy(() -> McpTokenVerifier.of(malformed))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(McpTokenVerifier.MALFORMED_HASH)
          .hasMessageNotContaining(malformed);
    }
  }
}
