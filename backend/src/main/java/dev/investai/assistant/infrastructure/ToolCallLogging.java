package dev.investai.assistant.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.investai.shared.api.RequestIdFilter;
import dev.investai.shared.logging.LogSanitizer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wraps every MCP tool and prompt (spec 3.0, criterion 11; spec 3.1, criterion 36):
 *
 * <ul>
 *   <li>one log line per call: tool, status, duration, size of the result, the code of an expected
 *       refusal, and the {@code requestId} of the MDC; never the arguments nor the result;
 *   <li>an expected refusal ({@link ToolCallRejected}) becomes {@code {"code", "field",
 *       "requestId"}}, without the value received;
 *   <li>any other error never reaches the client with an exception message: it becomes {@code
 *       tool.failed} with the {@code requestId}, to look up the logs;
 *   <li>one log line per prompt read: prompt, status, duration.
 * </ul>
 */
final class ToolCallLogging {

  /** Code of the generic error returned instead of any tool failure. */
  static final String TOOL_FAILED = "tool.failed";

  private static final Logger LOG = LoggerFactory.getLogger(ToolCallLogging.class);
  private static final String OK = "ok";
  private static final String ERROR = "error";

  private final JsonMapper json;

  ToolCallLogging(JsonMapper json) {
    this.json = json;
  }

  /**
   * Returns the same tool, logged and with errors made generic.
   *
   * @param tool the tool
   * @return the wrapped tool
   */
  SyncToolSpecification wrap(SyncToolSpecification tool) {
    String name = tool.tool().name();
    BiFunction<McpTransportContext, CallToolRequest, CallToolResult> handler = tool.callHandler();
    return new SyncToolSpecification(
        tool.tool(), (context, request) -> call(name, handler, context, request));
  }

  /**
   * Returns the same prompt, logged.
   *
   * @param prompt the prompt
   * @return the wrapped prompt
   */
  SyncPromptSpecification wrap(SyncPromptSpecification prompt) {
    String name = prompt.prompt().name();
    BiFunction<McpTransportContext, GetPromptRequest, GetPromptResult> handler =
        prompt.promptHandler();
    return new SyncPromptSpecification(
        prompt.prompt(), (context, request) -> get(name, handler, context, request));
  }

  private CallToolResult call(
      String name,
      BiFunction<McpTransportContext, CallToolRequest, CallToolResult> handler,
      McpTransportContext context,
      CallToolRequest request) {
    long start = System.nanoTime();
    @Nullable CallToolResult answer = null;
    @Nullable String code = null;
    try {
      answer = generic(handler.apply(context, request));
      return answer;
    } catch (UndeclaredThrowableException e) {
      // A checked exception of the tool method, rethrown by the annotation callback: its message
      // would reach the client through a JSON-RPC error.
      Throwable cause = e.getCause();
      if (cause instanceof ToolCallRejected rejected) {
        code = rejected.code();
        answer = refusal(rejected);
        return answer;
      }
      // The tool name is on the "MCP tool call" line that follows, with the same requestId.
      LOG.error("MCP tool failed with a checked exception", cause);
      answer = failure();
      return answer;
    } finally {
      // Also when an McpError (protocol error, deliberate) or an Error propagates: status=error.
      log(name, answer, code, start);
    }
  }

  private static GetPromptResult get(
      String name,
      BiFunction<McpTransportContext, GetPromptRequest, GetPromptResult> handler,
      McpTransportContext context,
      GetPromptRequest request) {
    long start = System.nanoTime();
    String status = ERROR;
    try {
      GetPromptResult result = handler.apply(context, request);
      status = OK;
      return result;
    } finally {
      String prompt = LogSanitizer.oneLine(name);
      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      LOG.info("MCP prompt get: prompt={} status={} durationMs={}", prompt, status, durationMs);
    }
  }

  /**
   * The annotation callbacks catch the runtime exceptions of the tool method and return an error
   * result carrying their message (possibly an argument value): replaced by the generic failure. A
   * tool logs the cause of the failures it expects itself, without argument values ({@link
   * HealthTool}).
   */
  private CallToolResult generic(CallToolResult result) {
    return Boolean.TRUE.equals(result.isError()) ? failure() : result;
  }

  private void log(
      String name, @Nullable CallToolResult result, @Nullable String code, long start) {
    String tool = LogSanitizer.oneLine(name);
    String status = result == null || Boolean.TRUE.equals(result.isError()) ? ERROR : OK;
    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    long resultBytes = result == null ? 0 : size(result);
    if (code == null) {
      LOG.info(
          "MCP tool call: tool={} status={} durationMs={} resultBytes={}",
          tool,
          status,
          durationMs,
          resultBytes);
      return;
    }
    // A constant of ToolCallRejected, never a text of the client.
    String refusal = LogSanitizer.oneLine(code);
    LOG.info(
        "MCP tool call: tool={} status={} durationMs={} resultBytes={} code={}",
        tool,
        status,
        durationMs,
        resultBytes,
        refusal);
  }

  /** Size of what the client receives; the SDK copies structured content as text when alone. */
  private long size(CallToolResult result) {
    Object structured = result.structuredContent();
    if (result.content().isEmpty() && structured != null) {
      return json.writeValueAsString(structured).getBytes(StandardCharsets.UTF_8).length;
    }
    return textBytes(result.content());
  }

  private CallToolResult failure() {
    return error(new Failure(TOOL_FAILED, null, requestId()));
  }

  private CallToolResult refusal(ToolCallRejected rejected) {
    return error(new Failure(rejected.code(), rejected.field(), requestId()));
  }

  private CallToolResult error(Failure failure) {
    String body = json.writeValueAsString(failure);
    return CallToolResult.builder().isError(true).addTextContent(body).build();
  }

  private static String requestId() {
    String requestId = MDC.get(RequestIdFilter.MDC_KEY);
    return requestId == null ? "" : requestId;
  }

  private static long textBytes(List<Content> content) {
    return content.stream()
        .filter(TextContent.class::isInstance)
        .map(TextContent.class::cast)
        .mapToLong(text -> text.text().getBytes(StandardCharsets.UTF_8).length)
        .sum();
  }

  /**
   * Body of an error, in a fixed field order.
   *
   * @param code {@link #TOOL_FAILED} or the code of an expected refusal
   * @param field the parameter at fault, absent when the error is not about one
   * @param requestId identifier of the request, to look up the logs
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record Failure(String code, @Nullable String field, String requestId) {}
}
