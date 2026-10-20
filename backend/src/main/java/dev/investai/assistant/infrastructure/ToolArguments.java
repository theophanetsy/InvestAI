package dev.investai.assistant.infrastructure;

import dev.investai.market.domain.InstrumentId;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Strict reading of the arguments of a tool call (spec 3.1, criterion 9). The input schema declared
 * to the client is only a hint: every rule is checked here, on the server, and a breach gives
 * {@code tool.invalid-argument} with the name of the parameter, never its value. Unknown arguments,
 * explicit {@code null}, wrong JSON types, decimals for integers and texts out of their bounds are
 * refused; lengths are checked before any pattern, so a huge text is never scanned.
 */
final class ToolArguments {

  /** Field reported for an argument the tool does not declare (its name is the client's text). */
  static final String ARGUMENTS = "arguments";

  /** An instrument identifier: a UUID in its canonical form. */
  static final String UUID_PATTERN =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

  /** Length of {@link #UUID_PATTERN}. */
  static final int UUID_LENGTH = 36;

  private static final Pattern UUID_FORMAT = Pattern.compile(UUID_PATTERN);

  private final Map<?, ?> values;
  private final @Nullable String reportedField;

  private ToolArguments(Map<?, ?> values, @Nullable String reportedField) {
    this.values = values;
    this.reportedField = reportedField;
  }

  /**
   * Reads the arguments of a call.
   *
   * @param request the call
   * @param names the parameters of the tool
   * @return the arguments
   * @throws ToolCallRejected when an argument is not a parameter of the tool
   */
  static ToolArguments of(CallToolRequest request, Set<String> names) throws ToolCallRejected {
    Map<String, Object> arguments = request.arguments();
    return checked(arguments == null ? Map.of() : arguments, names, null);
  }

  /**
   * Reads an object of an array argument; every error is reported on the array.
   *
   * @param element the element
   * @param names its properties
   * @param field name of the array parameter
   * @return the properties of the element
   * @throws ToolCallRejected when it is not an object of those properties
   */
  static ToolArguments item(@Nullable Object element, Set<String> names, String field)
      throws ToolCallRejected {
    if (!(element instanceof Map<?, ?> map)) {
      throw ToolCallRejected.invalidArgument(field);
    }
    return checked(map, names, field);
  }

  private static ToolArguments checked(
      Map<?, ?> values, Set<String> names, @Nullable String reportedField) throws ToolCallRejected {
    if (!names.containsAll(values.keySet())) {
      throw ToolCallRejected.invalidArgument(reportedField == null ? ARGUMENTS : reportedField);
    }
    return new ToolArguments(values, reportedField);
  }

  /**
   * Reads an optional text.
   *
   * @param name the parameter
   * @param maxLength maximum number of code points
   * @param format anchored pattern the text must match
   * @return the text, empty when absent
   * @throws ToolCallRejected when not a string, too long or not matching
   */
  Optional<String> text(String name, int maxLength, Pattern format) throws ToolCallRejected {
    Object value = raw(name);
    if (value == null) {
      return Optional.empty();
    }
    if (!(value instanceof String text)
        || text.length() > 2 * maxLength
        || text.codePointCount(0, text.length()) > maxLength
        || !format.matcher(text).matches()) {
      throw invalid(name);
    }
    return Optional.of(text);
  }

  /**
   * Reads a required text.
   *
   * @param name the parameter
   * @param maxLength maximum number of code points
   * @param format anchored pattern the text must match
   * @return the text
   * @throws ToolCallRejected when absent or invalid
   */
  String requiredText(String name, int maxLength, Pattern format) throws ToolCallRejected {
    return text(name, maxLength, format).orElseThrow(() -> invalid(name));
  }

  /**
   * Reads a required instrument identifier.
   *
   * @param name the parameter
   * @return the identifier
   * @throws ToolCallRejected when absent or not a UUID
   */
  InstrumentId instrumentId(String name) throws ToolCallRejected {
    return new InstrumentId(UUID.fromString(requiredText(name, UUID_LENGTH, UUID_FORMAT)));
  }

  /**
   * Reads an optional constant of an enumeration, spelt exactly.
   *
   * @param name the parameter
   * @param type the enumeration
   * @param defaultValue value when absent
   * @param <E> the enumeration
   * @return the constant
   * @throws ToolCallRejected when not one of the constants
   */
  <E extends Enum<E>> E constant(String name, Class<E> type, E defaultValue)
      throws ToolCallRejected {
    Object value = raw(name);
    if (value == null) {
      return defaultValue;
    }
    return Arrays.stream(type.getEnumConstants())
        .filter(constant -> constant.name().equals(value))
        .findFirst()
        .orElseThrow(() -> invalid(name));
  }

  /**
   * Reads a required constant of an enumeration, spelt exactly.
   *
   * @param name the parameter
   * @param type the enumeration
   * @param <E> the enumeration
   * @return the constant
   * @throws ToolCallRejected when absent or not one of the constants
   */
  <E extends Enum<E>> E requiredConstant(String name, Class<E> type) throws ToolCallRejected {
    if (raw(name) == null) {
      throw invalid(name);
    }
    return constant(name, type, type.getEnumConstants()[0]);
  }

  /**
   * Reads an optional whole number; a decimal number is refused, never truncated.
   *
   * @param name the parameter
   * @param min lowest value
   * @param max highest value
   * @param defaultValue value when absent
   * @return the number
   * @throws ToolCallRejected when not an integer within the bounds
   */
  int integer(String name, int min, int max, int defaultValue) throws ToolCallRejected {
    Object value = raw(name);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Integer number) || number < min || number > max) {
      throw invalid(name);
    }
    return number;
  }

  /**
   * Reads a required array.
   *
   * @param name the parameter
   * @param minItems fewest elements
   * @param maxItems most elements
   * @return the elements
   * @throws ToolCallRejected when absent, not an array or out of the bounds
   */
  List<?> list(String name, int minItems, int maxItems) throws ToolCallRejected {
    Object value = raw(name);
    if (!(value instanceof List<?> elements)
        || elements.size() < minItems
        || elements.size() > maxItems) {
      throw invalid(name);
    }
    return elements;
  }

  /**
   * Parses a text already checked by an anchored decimal pattern.
   *
   * @param text a short decimal number
   * @return its exact value
   * @throws IllegalArgumentException when it is not a number (never after validation)
   */
  static BigDecimal decimal(String text) {
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Not a decimal number", ex);
    }
  }

  /** The value of a parameter; an explicit JSON {@code null} is refused, not taken as absent. */
  private @Nullable Object raw(String name) throws ToolCallRejected {
    Object value = values.get(name);
    if (value == null && values.containsKey(name)) {
      throw invalid(name);
    }
    return value;
  }

  private ToolCallRejected invalid(String name) {
    return ToolCallRejected.invalidArgument(reportedField == null ? name : reportedField);
  }
}
