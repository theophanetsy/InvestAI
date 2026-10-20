package dev.investai.assistant.infrastructure;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * An expected refusal of a tool call (spec 3.1, criterion 9), answered by {@link ToolCallLogging}
 * as {@code {"code", "field", "requestId"}}: never the value received.
 *
 * <p>Checked on purpose: the annotation callbacks of Spring AI turn a runtime exception into an
 * error result carrying its message, but rethrow a checked one (wrapped), which {@link
 * ToolCallLogging} then sees with its type.
 */
public final class ToolCallRejected extends Exception {

  /** An argument is missing, unknown, of the wrong type or out of its bounds. */
  public static final String INVALID_ARGUMENT = "tool.invalid-argument";

  /** The instrument is not in the reference data. */
  public static final String INSTRUMENT_NOT_FOUND = "instrument.not-found";

  @Serial private static final long serialVersionUID = 1L;

  private final String code;
  private final @Nullable String field;

  private ToolCallRejected(String code, @Nullable String field, @Nullable Throwable cause) {
    // Constant text only: the message may be logged.
    super(field == null ? code : code + " on " + field, cause, false, false);
    this.code = code;
    this.field = field;
  }

  /**
   * An invalid argument.
   *
   * @param field name of the parameter, a constant of the tool (never the client's text)
   * @return the refusal
   */
  static ToolCallRejected invalidArgument(String field) {
    return new ToolCallRejected(INVALID_ARGUMENT, field, null);
  }

  /**
   * An unknown instrument.
   *
   * @param cause the error of the use case
   * @return the refusal
   */
  static ToolCallRejected instrumentNotFound(Throwable cause) {
    return new ToolCallRejected(INSTRUMENT_NOT_FOUND, null, cause);
  }

  /**
   * Returns the stable code.
   *
   * @return {@value #INVALID_ARGUMENT} or {@value #INSTRUMENT_NOT_FOUND}
   */
  public String code() {
    return code;
  }

  /**
   * Returns the parameter at fault.
   *
   * @return its name, {@code null} when the refusal is not about one parameter
   */
  public @Nullable String field() {
    return field;
  }
}
