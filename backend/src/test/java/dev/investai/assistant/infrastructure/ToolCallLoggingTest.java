package dev.investai.assistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.investai.shared.api.RequestIdFilter;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class ToolCallLoggingTest {

  private static final String SAMPLE = "sample";
  private static final String REQUEST_ID = "4f0c1d2e-0000-4000-8000-000000000001";
  private static final String GENERIC_FAILURE =
      "{\"code\":\"tool.failed\",\"requestId\":\"" + REQUEST_ID + "\"}";

  /** What an annotation callback returns when the method fails on a free-text argument. */
  private static final String LEAKY_MESSAGE = "Error invoking method: objectif=placer 50 000 €";

  private static final String SECRET_PART = "objectif";
  private static final String ERROR_LINE = "MCP tool call: tool=sample status=error";

  private final ToolCallLogging logging = new ToolCallLogging(JsonMapper.builder().build());
  private final CallToolRequest request = CallToolRequest.builder(SAMPLE).build();

  @BeforeEach
  void setRequestId() {
    MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void errorResultBecomesGenericWithTheRequestIdAndIsLogged(CapturedOutput output) {
    CallToolResult result =
        call(
            (context, call) ->
                CallToolResult.builder().isError(true).addTextContent(LEAKY_MESSAGE).build());

    assertThat(result.isError()).isTrue();
    assertThat(text(result)).isEqualTo(GENERIC_FAILURE);
    assertThat(output.getOut()).contains(ERROR_LINE).doesNotContain(SECRET_PART);
  }

  @Test
  void checkedExceptionOfTheToolBecomesGenericWithoutItsMessage(CapturedOutput output) {
    CallToolResult result =
        call(
            (context, call) -> {
              throw new UndeclaredThrowableException(new IOException(LEAKY_MESSAGE));
            });

    assertThat(text(result)).isEqualTo(GENERIC_FAILURE);
    assertThat(output.getOut()).contains(ERROR_LINE, "MCP tool failed with a checked exception");
  }

  @Test
  void protocolErrorGoesThroughButIsLoggedAsAnError(CapturedOutput output) {
    var protocolError = McpError.builder(ErrorCodes.INVALID_PARAMS).message("bad call").build();

    assertThatThrownBy(
            () ->
                call(
                    (context, call) -> {
                      throw protocolError;
                    }))
        .isSameAs(protocolError);
    assertThat(output.getOut()).contains(ERROR_LINE, "resultBytes=0");
  }

  @Test
  void structuredResultIsLeftAsIsAndMeasuredAsTheTextTheClientReceives(CapturedOutput output) {
    CallToolResult result =
        call(
            (context, call) ->
                CallToolResult.builder().structuredContent(Map.of("status", "READY")).build());

    assertThat(result.isError()).isNotEqualTo(true);
    assertThat(result.structuredContent()).isEqualTo(Map.of("status", "READY"));
    assertThat(output.getOut())
        .contains("tool=sample status=ok", "resultBytes=18")
        .doesNotContain("READY");
  }

  @Test
  void textResultIsMeasuredInUtf8Bytes(CapturedOutput output) {
    CallToolResult result =
        call((context, call) -> CallToolResult.builder().addTextContent("é").build());

    assertThat(text(result)).isEqualTo("é");
    assertThat(output.getOut()).contains("resultBytes=2");
  }

  @Test
  void invalidArgumentGivesItsCodeAndFieldButNeverTheValueAndIsLoggedWithItsCode(
      CapturedOutput output) {
    CallToolResult result =
        call(
            (context, call) -> {
              throw new UndeclaredThrowableException(ToolCallRejected.invalidArgument("query"));
            });

    assertThat(result.isError()).isTrue();
    assertThat(text(result))
        .isEqualTo(
            "{\"code\":\"tool.invalid-argument\",\"field\":\"query\",\"requestId\":\""
                + REQUEST_ID
                + "\"}");
    assertThat(output.getOut())
        .contains(ERROR_LINE, "code=tool.invalid-argument")
        .doesNotContain("MCP tool failed");
  }

  @Test
  void unknownInstrumentGivesItsCodeWithoutField(CapturedOutput output) {
    CallToolResult result =
        call(
            (context, call) -> {
              throw new UndeclaredThrowableException(
                  ToolCallRejected.instrumentNotFound(new IllegalStateException(SECRET_PART)));
            });

    assertThat(text(result))
        .isEqualTo("{\"code\":\"instrument.not-found\",\"requestId\":\"" + REQUEST_ID + "\"}");
    assertThat(output.getOut()).contains("code=instrument.not-found").doesNotContain(SECRET_PART);
  }

  @Test
  void promptReadIsLoggedOnceWithoutItsText(CapturedOutput output) {
    var text = "Tu aides le propriétaire";
    var prompt = McpSchema.Prompt.builder(SAMPLE).build();
    var result =
        McpSchema.GetPromptResult.builder(
                List.of(
                    McpSchema.PromptMessage.builder(
                            McpSchema.Role.USER, TextContent.builder(text).build())
                        .build()))
            .build();
    SyncPromptSpecification logged =
        logging.wrap(new SyncPromptSpecification(prompt, (context, get) -> result));

    var got =
        logged
            .promptHandler()
            .apply(McpTransportContext.EMPTY, McpSchema.GetPromptRequest.builder(SAMPLE).build());

    assertThat(got).isSameAs(result);
    assertThat(output.getOut())
        .contains("MCP prompt get: prompt=sample status=ok durationMs=")
        .doesNotContain(text);
  }

  @Test
  void failedPromptReadIsLoggedAsAnError(CapturedOutput output) {
    var failure = new IllegalStateException("broken");
    SyncPromptSpecification logged =
        logging.wrap(
            new SyncPromptSpecification(
                McpSchema.Prompt.builder(SAMPLE).build(),
                (context, get) -> {
                  throw failure;
                }));

    assertThatThrownBy(
            () ->
                logged
                    .promptHandler()
                    .apply(
                        McpTransportContext.EMPTY,
                        McpSchema.GetPromptRequest.builder(SAMPLE).build()))
        .isSameAs(failure);
    assertThat(output.getOut()).contains("MCP prompt get: prompt=sample status=error");
  }

  private CallToolResult call(
      BiFunction<McpTransportContext, CallToolRequest, CallToolResult> body) {
    Tool definition = Tool.builder(SAMPLE, Map.of("type", "object")).build();
    SyncToolSpecification tool = logging.wrap(new SyncToolSpecification(definition, body));
    return tool.callHandler().apply(McpTransportContext.EMPTY, request);
  }

  private static String text(CallToolResult result) {
    assertThat(result.content()).hasSize(1);
    return ((TextContent) result.content().getFirst()).text();
  }
}
