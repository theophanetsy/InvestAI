package dev.investai.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.investai.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The OpenAPI contract served by springdoc is the one versioned in {@code api/openapi.json}, from
 * which the Angular client is generated (ADR-0004).
 */
@IntegrationTest
class OpenApiContractIT {

  private static final Path COMMITTED = Path.of("..", "api", "openapi.json");
  private static final Path CURRENT = Path.of("target", "openapi.json");
  private static final String API_DOCS = "/v3/api-docs";
  private static final String PROBLEM_REF = "#/components/schemas/Problem";
  private static final String CSRF_COOKIE = "XSRF-TOKEN";

  @Autowired private MockMvcTester mvc;
  @Autowired private JsonMapper json;

  @Test
  void servedContractIsTheVersionedOne() throws IOException {
    String served = servedContract();
    Files.createDirectories(CURRENT.getParent());
    Files.writeString(CURRENT, served, StandardCharsets.UTF_8);

    assertThat(COMMITTED).as("versioned contract api/openapi.json").exists();
    assertThat(json.readTree(Files.readString(COMMITTED, StandardCharsets.UTF_8)))
        .as(
            "api/openapi.json differs from the API: run `npm run api:update` from the repository"
                + " root (copies backend/target/openapi.json and regenerates the Angular client)")
        .isEqualTo(json.readTree(served));
  }

  @Test
  void contractRequiresSession() {
    assertThat(mvc.get().uri(API_DOCS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void serverIsTheFrontendOrigin() {
    assertThat(contract().get("servers"))
        .singleElement()
        .satisfies(
            server -> {
              assertThat(server.get("url").asString()).isEmpty();
            });
  }

  @Test
  void everyOperationHasReadableIdModuleTagAndProblemResponse() {
    List<String> operationIds = new ArrayList<>();
    for (JsonNode operation : operations()) {
      String id = operation.get("operationId").asString();
      operationIds.add(id);
      assertThat(id).as("operationId").matches("[a-z][A-Za-z]*");
      assertThat(operation.get("tags").valueStream().map(JsonNode::asString))
          .as("tags of %s", id)
          .singleElement()
          .isIn("system", "auth", "market");
      assertThat(
              operation
                  .at("/responses/default/content/application~1problem+json/schema/$ref")
                  .asString())
          .as("default error response of %s", id)
          .isEqualTo(PROBLEM_REF);
    }
    assertThat(operationIds)
        .doesNotHaveDuplicates()
        .contains(
            "getStatus",
            "login",
            "logout",
            "me",
            "searchInstruments",
            "getInstrument",
            "getInstrumentPrices",
            "trackInstrument");
  }

  @Test
  void onlyTheVersionedJsonApiIsDescribed() {
    JsonNode paths = contract().get("paths");

    assertThat(names(paths)).allSatisfy(path -> assertThat(path).startsWith("/api/v1/"));
    assertThat(paths.findValues("content"))
        .flatMap(OpenApiContractIT::names)
        .containsOnly("application/json", "application/problem+json");
  }

  @Test
  void problemSchemaDescribesTheErrorContract() {
    JsonNode problem = contract().at("/components/schemas/Problem");

    assertThat(names(problem.get("properties")))
        .containsExactlyInAnyOrder(
            "type", "title", "status", "instance", "code", "requestId", "errors");
    assertThat(problem.get("required").valueStream().map(JsonNode::asString))
        .containsExactlyInAnyOrder("title", "status", "code", "requestId");
    assertThat(contract().at("/components/schemas/FieldViolation/required").valueStream())
        .map(JsonNode::asString)
        .containsExactlyInAnyOrder("field", "code");
  }

  @Test
  void dtoFieldsAreRequiredUnlessNullable() {
    JsonNode schemas = contract().get("components").get("schemas");

    assertThat(schemas.at("/StatusResponse/required").valueStream().map(JsonNode::asString))
        .containsExactlyInAnyOrder("application", "serverTime");
    assertThat(schemas.at("/LoginRequest/required").valueStream().map(JsonNode::asString))
        .containsExactlyInAnyOrder("username", "password");
  }

  @Test
  void nullFieldsAreAbsentFromJson() {
    assertThat(json.writeValueAsString(new Sample("value", null)))
        .isEqualTo("{\"present\":\"value\"}");
  }

  @Test
  void actualErrorResponsesMatchTheProblemSchema() {
    var unauthenticated = mvc.get().uri("/api/v1/auth/me").exchange();
    // Real CSRF exchange, like the browser: csrf() would swap the shared CsrfFilter repository
    // for a session-based one and break the tests that go through the server (AuthApiIT).
    Cookie csrf =
        Objects.requireNonNull(unauthenticated.getResponse().getCookie(CSRF_COOKIE), CSRF_COOKIE);
    var invalid =
        mvc.post()
            .uri("/api/v1/auth/login")
            .cookie(csrf)
            .header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"\",\"password\":\"\"}")
            .exchange();
    assertThat(unauthenticated).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(invalid).hasStatus(HttpStatus.BAD_REQUEST);
    JsonNode schemas = contract().at("/components/schemas");

    for (var response : List.of(unauthenticated, invalid)) {
      JsonNode body = json.readTree(response.getResponse().getContentAsByteArray());
      assertMatches(body, schemas.get("Problem"));
      body.path("errors")
          .valueStream()
          .forEach(violation -> assertMatches(violation, schemas.get("FieldViolation")));
    }
    assertThat(json.readTree(invalid.getResponse().getContentAsByteArray()).get("errors"))
        .as("validation errors")
        .isNotEmpty();
  }

  @Test
  void noDocumentationUi() {
    assertThat(mvc.get().uri(API_DOCS + ".yaml").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.get().uri("/swagger-ui.html").with(user("someone")).exchange())
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void logoutHandledBySpringSecurityIsDocumented() {
    JsonNode logout = contract().at("/paths/~1api~1v1~1auth~1logout/post");

    assertThat(logout.get("operationId").asString()).isEqualTo("logout");
    assertThat(names(logout.get("responses"))).contains("204", "default");
  }

  private String servedContract() {
    var response = mvc.get().uri(API_DOCS).with(user("someone")).exchange();
    assertThat(response).hasStatus(HttpStatus.OK);
    // Same bytes on every OS: the file is copied into the repository as is.
    return new String(response.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
        + "\n";
  }

  private JsonNode contract() {
    return json.readTree(servedContract());
  }

  private List<JsonNode> operations() {
    List<JsonNode> operations = new ArrayList<>();
    for (Map.Entry<String, JsonNode> path : contract().get("paths").properties()) {
      path.getValue().values().forEach(operations::add);
    }
    return operations;
  }

  private static List<String> names(JsonNode object) {
    return object.propertyNames().stream().toList();
  }

  /** Every field is declared by the schema and every required field is present. */
  private static void assertMatches(JsonNode body, JsonNode schema) {
    assertThat(names(body)).isSubsetOf(names(schema.get("properties")));
    assertThat(schema.get("required").valueStream().map(JsonNode::asString))
        .isSubsetOf(names(body));
  }

  record Sample(String present, @Nullable String absent) {}
}
