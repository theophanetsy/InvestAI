package dev.investai.shared.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import org.jspecify.annotations.Nullable;

/**
 * Carries the Java null contract into the OpenAPI contract (ADR-0004): every component of an
 * application record is {@code required} unless it is annotated {@link Nullable}, as NullAway
 * enforces on the Java side. The generated TypeScript then has no needless optional fields.
 *
 * <p>Registered with springdoc as a bean ({@link OpenApiConfiguration}). A component renamed with
 * {@link JsonProperty} is handled; a global Jackson naming strategy is not (none is configured).
 */
final class RequiredUnlessNullableConverter implements ModelConverter {

  private static final String APPLICATION_PACKAGE = "dev.investai.";
  private static final String REF_PREFIX = "#/components/schemas/";

  @Override
  public @Nullable Schema<?> resolve(
      AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
    if (!chain.hasNext()) {
      return null;
    }
    Schema<?> resolved = chain.next().resolve(type, context, chain);
    Class<?> raw = Json31.mapper().constructType(type.getType()).getRawClass();
    if (resolved != null && raw.isRecord() && raw.getName().startsWith(APPLICATION_PACKAGE)) {
      Schema<?> model = definition(resolved, context);
      if (model != null) {
        markRequired(model, raw.getRecordComponents());
      }
    }
    return resolved;
  }

  /** The resolved schema is either the model itself or a local reference to its definition. */
  private static @Nullable Schema<?> definition(Schema<?> resolved, ModelConverterContext context) {
    String ref = resolved.get$ref();
    if (ref == null) {
      return resolved;
    }
    return ref.startsWith(REF_PREFIX)
        ? context.getDefinedModels().get(ref.substring(REF_PREFIX.length()))
        : null;
  }

  private static void markRequired(Schema<?> model, RecordComponent... components) {
    for (RecordComponent component : components) {
      String name = jsonName(component);
      boolean nullable = component.getAnnotatedType().isAnnotationPresent(Nullable.class);
      boolean known = model.getProperties() != null && model.getProperties().containsKey(name);
      boolean alreadyRequired = model.getRequired() != null && model.getRequired().contains(name);
      if (known && !nullable && !alreadyRequired) {
        model.addRequiredItem(name);
      }
    }
  }

  /** {@code @JsonProperty} on a record component lands on its accessor, among other places. */
  private static String jsonName(RecordComponent component) {
    JsonProperty property = component.getAccessor().getAnnotation(JsonProperty.class);
    return property == null || property.value().isEmpty() ? component.getName() : property.value();
  }
}
