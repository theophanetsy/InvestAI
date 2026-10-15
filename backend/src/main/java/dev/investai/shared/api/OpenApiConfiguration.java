package dev.investai.shared.api;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * OpenAPI contract conventions (ADR-0004): relative server, fields required unless nullable, and
 * the RFC 9457 error contract (ADR-0006) declared on every operation.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

  /** Schema name of the problem details; other modules may reference it. */
  public static final String PROBLEM_SCHEMA_REF = "#/components/schemas/Problem";

  private static final String PROBLEM_JSON = "application/problem+json";

  /**
   * Contract metadata. The empty server URL means "same origin as the frontend": the generated
   * client then issues relative requests ({@code /api/v1/...}).
   *
   * @return the base contract
   */
  @Bean
  OpenAPI investaiOpenApi() {
    return new OpenAPI()
        .info(new Info().title("InvestAI API").version("v1"))
        .servers(List.of(new Server().url("").description("Même origine que le frontend")));
  }

  /**
   * Java null contract carried into the schemas.
   *
   * @return the converter, registered by springdoc
   */
  @Bean
  RequiredUnlessNullableConverter requiredUnlessNullableConverter() {
    return new RequiredUnlessNullableConverter();
  }

  /**
   * Declares the problem schema and an error response on every operation. Runs last, so that
   * operations added by other customizers (e.g. logout) get it too.
   *
   * @param springDoc springdoc settings (OpenAPI 3.1 or 3.0)
   * @return the customizer
   */
  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  OpenApiCustomizer problemResponses(SpringDocConfigProperties springDoc) {
    return openApi -> {
      ResolvedSchema problem =
          ModelConverters.getInstance(springDoc.isOpenapi31())
              .readAllAsResolvedSchema(ProblemSchema.class);
      if (openApi.getComponents() == null) {
        openApi.components(new Components());
      }
      problem.referencedSchemas.forEach(openApi.getComponents()::addSchemas);
      ApiResponse error =
          new ApiResponse()
              .description("Erreur (RFC 9457) : voir `code`")
              .content(
                  new Content()
                      .addMediaType(
                          PROBLEM_JSON,
                          new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA_REF))));
      openApi
          .getPaths()
          .values()
          .forEach(
              path ->
                  path.readOperations()
                      .forEach(
                          operation -> operation.getResponses().addApiResponse("default", error)));
    };
  }
}
