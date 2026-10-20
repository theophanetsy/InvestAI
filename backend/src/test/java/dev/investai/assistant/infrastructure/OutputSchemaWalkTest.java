package dev.investai.assistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.support.McpTestClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The walk of the output schemas does find free text outside untrusted (no vacuous pass). */
class OutputSchemaWalkTest {

  @Test
  void reportsAFreeTextOutsideUntrustedEvenBehindAReferenceOrInAnArray() {
    var schema =
        McpTestClient.json(
            """
            {"$defs":{"Item":{"type":"object","properties":{"name":{"type":"string"}}}},
             "type":"object","properties":{
               "code":{"type":"string","enum":["A"]},
               "asOf":{"type":"string","format":"date"},
               "weightPercent":{"type":"string"},
               "untrusted":{"type":"object","properties":{"name":{"type":"string"}}},
               "items":{"type":"array","items":{"$ref":"#/$defs/Item"}},
               "comment":{"type":"string"}}}
            """);
    List<String> outside = new ArrayList<>();

    OutputSchemaWalk.freeTextOutsideUntrusted(schema, "tool", outside);

    assertThat(outside).containsExactlyInAnyOrder("tool.items[].name", "tool.comment");
  }
}
