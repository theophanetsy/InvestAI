package dev.investai.assistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.investai.assistant.application.AssistantHealth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(OutputCaptureExtension.class)
class HealthToolTest {

  private final AssistantHealth health = mock(AssistantHealth.class);
  private final HealthTool tool = new HealthTool(health);

  @Test
  void databaseFailureIsLoggedWithItsCauseThenPropagated(CapturedOutput output) {
    var failure = new DataAccessResourceFailureException("connection refused");
    when(health.report()).thenThrow(failure);

    assertThatThrownBy(tool::getHealth).isSameAs(failure);
    // The annotation callback only keeps the message: the cause must be in the logs.
    assertThat(output.getOut())
        .contains("MCP tool getHealth: database unavailable", "DataAccessResourceFailureException");
  }
}
