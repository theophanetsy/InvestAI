package dev.investai.auth.infrastructure;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Logout is handled by a Spring Security filter, invisible to springdoc: it is added to the OpenAPI
 * contract by hand so that the Angular client gets it (ADR-0004).
 */
@Configuration(proxyBeanMethods = false)
public class LogoutOpenApiConfiguration {

  /**
   * Documents {@code POST /api/v1/auth/logout}.
   *
   * @return the customizer, applied before the shared error responses
   */
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  OpenApiCustomizer logoutOperation() {
    // A new operation on each run: later customizers modify it (shared error response).
    return openApi ->
        openApi.path(SecurityConfiguration.LOGOUT_PATH, new PathItem().post(logout()));
  }

  private static Operation logout() {
    return new Operation()
        .operationId("logout")
        .addTagsItem("auth")
        .summary("Ferme la session (cookie supprimé)")
        .responses(
            new ApiResponses()
                .addApiResponse("204", new ApiResponse().description("Session fermée")));
  }
}
