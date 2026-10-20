package dev.investai.assistant.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Walks an output schema to find text fields outside {@code untrusted} (spec 3.1, criterion 44). A
 * string is accepted outside only when it cannot carry free text: an enumeration, a format (date,
 * date-time, uuid), or a field of the application listed here (decimal strings, codes, constant
 * texts). A new string field fails the test until it is reviewed and listed, or moved under {@code
 * untrusted}.
 */
final class OutputSchemaWalk {

  /** Strings of the application: decimals, identifiers and constant texts, never third-party. */
  private static final Set<String> APPLICATION_STRINGS =
      Set.of(
          // Decimal strings.
          "amount",
          "value",
          "price",
          "close",
          "correlation",
          "minAverageDailyTradedAmountEur",
          // ISO and exchange codes, validated by the market module.
          "currency",
          "isin",
          "mic",
          // Constant texts of the application.
          "label",
          "description",
          "text",
          "basis",
          "version");

  private static final String UNTRUSTED = "untrusted";

  private OutputSchemaWalk() {}

  /**
   * Lists the free-text fields outside {@code untrusted}.
   *
   * @param schema the output schema of a tool
   * @param tool its name, to report the path
   * @param outside the list to add the paths to
   */
  static void freeTextOutsideUntrusted(JsonNode schema, String tool, List<String> outside) {
    walk(schema, schema.path("$defs"), tool, outside);
  }

  private static void walk(JsonNode node, JsonNode defs, String path, List<String> outside) {
    JsonNode resolved = resolve(node, defs);
    for (Map.Entry<String, JsonNode> property : resolved.path("properties").properties()) {
      String name = property.getKey();
      if (UNTRUSTED.equals(name)) {
        continue;
      }
      JsonNode field = resolve(property.getValue(), defs);
      if (isFreeText(field) && !applicationString(name)) {
        outside.add(path + "." + name);
      }
      walk(field, defs, path + "." + name, outside);
    }
    if (resolved.has("items")) {
      walk(resolved.get("items"), defs, path + "[]", outside);
    }
  }

  private static JsonNode resolve(JsonNode node, JsonNode defs) {
    JsonNode reference = node.get("$ref");
    if (reference == null) {
      return node;
    }
    String target = reference.asString();
    return defs.path(target.substring(target.lastIndexOf('/') + 1));
  }

  private static boolean isFreeText(JsonNode field) {
    return "string".equals(field.path("type").asString())
        && !field.has("enum")
        && !field.has("format");
  }

  private static boolean applicationString(String name) {
    return APPLICATION_STRINGS.contains(name) || name.endsWith("Percent");
  }
}
