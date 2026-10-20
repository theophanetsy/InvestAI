package dev.investai.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Talks to {@code /mcp} of the test server like an MCP client (Streamable HTTP, JSON-RPC over
 * POST), through the real servlet container: Spring Security, the firewall and the request
 * identifier all apply.
 */
public final class McpTestClient implements AutoCloseable {

  /** Bearer header of {@link FixtureCredentials#MCP_BEARER}. */
  public static final String VALID_AUTHORIZATION = "Bearer " + FixtureCredentials.MCP_BEARER;

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final HttpClient http = HttpClient.newHttpClient();
  private final int port;
  private int nextId = 1;

  /**
   * Creates a client of the test server.
   *
   * @param port the local server port
   */
  public McpTestClient(int port) {
    this.port = port;
  }

  /**
   * Sends a JSON-RPC request with the valid token.
   *
   * @param method the JSON-RPC method
   * @param params its parameters, as JSON
   * @return the HTTP response
   * @throws IOException on network errors
   * @throws InterruptedException when interrupted
   */
  public HttpResponse<String> call(String method, String params)
      throws IOException, InterruptedException {
    return post(request(method, params), "Authorization", VALID_AUTHORIZATION);
  }

  /**
   * Sends a JSON-RPC request with the valid token and returns its {@code result}.
   *
   * @param method the JSON-RPC method
   * @param params its parameters, as JSON
   * @return the result member
   * @throws IOException on network errors
   * @throws InterruptedException when interrupted
   */
  public JsonNode result(String method, String params) throws IOException, InterruptedException {
    HttpResponse<String> response = call(method, params);
    if (response.statusCode() != 200) {
      throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
    }
    return JSON.readTree(response.body()).get("result");
  }

  /**
   * Builds a JSON-RPC request.
   *
   * @param method the method
   * @param params its parameters, as JSON
   * @return the request body
   */
  public String request(String method, String params) {
    int id = nextId;
    nextId++;
    return """
        {"jsonrpc":"2.0","id":%d,"method":"%s","params":%s}\
        """
        .formatted(id, method, params);
  }

  /**
   * Posts a body to {@code /mcp} with the headers of an MCP client plus the given ones.
   *
   * @param body the JSON-RPC message
   * @param headers additional header names and values, alternating
   * @return the HTTP response
   * @throws IOException on network errors
   * @throws InterruptedException when interrupted
   */
  public HttpResponse<String> post(String body, String... headers)
      throws IOException, InterruptedException {
    List<String> all = new ArrayList<>(List.of(headers));
    all.addAll(
        List.of(
            "Content-Type", "application/json", "Accept", "application/json, text/event-stream"));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
            .headers(all.toArray(String[]::new))
            .POST(BodyPublishers.ofString(body))
            .build();
    return http.send(request, BodyHandlers.ofString());
  }

  /**
   * Parses a JSON body.
   *
   * @param body the body
   * @return the tree
   */
  public static JsonNode json(String body) {
    return JSON.readTree(body);
  }

  @Override
  public void close() {
    http.close();
  }
}
