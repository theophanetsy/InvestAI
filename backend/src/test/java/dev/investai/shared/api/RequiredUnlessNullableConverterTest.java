package dev.investai.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContextImpl;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Objects;
import jdk.net.UnixDomainPrincipal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RequiredUnlessNullableConverterTest {

  private final ModelConverters converters = new ModelConverters(true);

  RequiredUnlessNullableConverterTest() {
    converters.addConverter(new RequiredUnlessNullableConverter());
  }

  @Test
  void recordComponentsAreRequiredUnlessNullable() {
    var schemas = converters.readAll(Outer.class);

    assertThat(requiredOf(schemas.get("Outer"))).containsExactlyInAnyOrder("name", "inner", "tags");
    assertThat(requiredOf(schemas.get("Inner"))).containsExactly("value");
  }

  @Test
  void onlyApplicationRecordsAreChanged() {
    var schemas = converters.readAll(Wrapper.class);

    assertThat(schemas).containsKeys("Wrapper", "UnixDomainPrincipal");
    for (Schema<?> schema : schemas.values()) {
      assertThat(schema.getRequired()).as(schema.getName()).isNull();
    }
  }

  @Test
  void jsonNamesAreUsedAndIgnoredOrAlreadyRequiredComponentsAreSkipped() {
    var schemas = converters.readAll(Renamed.class);

    assertThat(requiredOf(schemas.get("Renamed"))).containsExactlyInAnyOrder("label", "json_name");
  }

  @Test
  void genericRecordsAreResolvedThroughTheirReference() {
    var schemas = converters.readAll(Holder.class);

    assertThat(requiredOf(schemas.get("PageInner"))).containsExactly("items");
  }

  @Test
  void nonLocalReferencesAreLeftAlone() {
    Schema<?> external = new Schema<>().$ref("https://example.org/schemas/Money");
    ModelConverter next = (type, context, chain) -> external;
    var context = new ModelConverterContextImpl(List.of());

    Schema<?> resolved =
        new RequiredUnlessNullableConverter()
            .resolve(new AnnotatedType(Inner.class), context, List.of(next).iterator());

    assertThat(resolved).isSameAs(external);
    assertThat(external.getRequired()).isNull();
  }

  private static List<String> requiredOf(@Nullable Schema<?> schema) {
    List<String> required = Objects.requireNonNull(schema).getRequired();
    return required == null ? List.of() : required;
  }

  record Outer(String name, @Nullable String comment, Inner inner, List<String> tags) {}

  record Inner(String value, @Nullable Integer optional) {}

  /** {@code label} is already required by Bean Validation: listed once. */
  record Renamed(
      @NotBlank String label,
      @JsonProperty("json_name") String javaName,
      @JsonIgnore String hidden) {}

  record Page<T>(List<T> items, @Nullable String next) {}

  record Holder(Page<Inner> page) {}

  /** Not a record: left to the default rules. */
  static final class Wrapper {
    public String getLabel() {
      return "label";
    }

    public @Nullable UnixDomainPrincipal getPrincipal() {
      return null;
    }
  }
}
