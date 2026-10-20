package dev.investai.assistant.infrastructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Builds the strict input schema a tool declares in {@code tools/list} (spec 3.1, criterion 9):
 * types, enumerations, bounds and patterns, unknown properties refused. Built from the same
 * constants as the checks of {@link ToolArguments}, which remain the rule: the schema is only a
 * hint for the client.
 */
final class InputSchema {

  private static final String TYPE = "type";
  private static final String STRING = "string";
  private static final String DESCRIPTION = "description";
  private static final String DEFAULT = "default";
  private static final String MAX_LENGTH = "maxLength";
  private static final String PATTERN = "pattern";

  private final Map<String, Object> properties = new LinkedHashMap<>();
  private final List<String> required = new ArrayList<>();

  /**
   * Adds a text.
   *
   * @param name the parameter
   * @param description what it is, in French
   * @param maxLength maximum number of characters
   * @param pattern anchored pattern
   * @param mandatory whether it is required
   * @return this schema
   */
  InputSchema text(
      String name, String description, int maxLength, String pattern, boolean mandatory) {
    Map<String, Object> property = property(STRING, description);
    property.put("minLength", 1);
    property.put(MAX_LENGTH, maxLength);
    property.put(PATTERN, pattern);
    return add(name, property, mandatory);
  }

  /**
   * Adds a required instrument identifier.
   *
   * @param name the parameter
   * @param description what it is, in French
   * @return this schema
   */
  InputSchema instrumentId(String name, String description) {
    Map<String, Object> property = property(STRING, description);
    property.put("format", "uuid");
    property.put(MAX_LENGTH, ToolArguments.UUID_LENGTH);
    property.put(PATTERN, ToolArguments.UUID_PATTERN);
    return add(name, property, true);
  }

  /**
   * Adds a constant of an enumeration.
   *
   * @param name the parameter
   * @param description what it is, in French
   * @param constants the allowed values
   * @param defaultValue the value when absent, {@code null} when the parameter is required
   * @return this schema
   */
  InputSchema constant(
      String name, String description, Enum<?>[] constants, @Nullable Enum<?> defaultValue) {
    Map<String, Object> property = property(STRING, description);
    property.put("enum", Arrays.stream(constants).map(Enum::name).toList());
    if (defaultValue != null) {
      property.put(DEFAULT, defaultValue.name());
    }
    return add(name, property, defaultValue == null);
  }

  /**
   * Adds an optional whole number.
   *
   * @param name the parameter
   * @param description what it is, in French
   * @param min lowest value
   * @param max highest value
   * @param defaultValue the value when absent
   * @return this schema
   */
  InputSchema integer(String name, String description, int min, int max, int defaultValue) {
    Map<String, Object> property = property("integer", description);
    property.put("minimum", min);
    property.put("maximum", max);
    property.put(DEFAULT, defaultValue);
    return add(name, property, false);
  }

  /**
   * Adds a required array of objects.
   *
   * @param name the parameter
   * @param description what it is, in French
   * @param minItems fewest elements
   * @param maxItems most elements
   * @param items the schema of each element
   * @return this schema
   */
  InputSchema array(
      String name, String description, int minItems, int maxItems, InputSchema items) {
    Map<String, Object> property = property("array", description);
    property.put("minItems", minItems);
    property.put("maxItems", maxItems);
    property.put("items", items.build());
    return add(name, property, true);
  }

  /**
   * Returns the names of the parameters.
   *
   * @return the names, for {@link ToolArguments}
   */
  Set<String> names() {
    return Set.copyOf(properties.keySet());
  }

  /**
   * Returns the JSON schema of an object of these parameters, nothing else allowed.
   *
   * @return the schema
   */
  Map<String, Object> build() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put(TYPE, "object");
    schema.put("properties", Collections.unmodifiableMap(new LinkedHashMap<>(properties)));
    schema.put("required", List.copyOf(required));
    schema.put("additionalProperties", false);
    return Collections.unmodifiableMap(schema);
  }

  private static Map<String, Object> property(String type, String description) {
    Map<String, Object> property = new LinkedHashMap<>();
    property.put(TYPE, type);
    property.put(DESCRIPTION, description);
    return property;
  }

  private InputSchema add(String name, Map<String, Object> property, boolean mandatory) {
    properties.put(name, property);
    if (mandatory) {
      required.add(name);
    }
    return this;
  }
}
