package dev.investai.market.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * Adds the EODHD key ({@code api_token}, which the API only accepts in the query string) at the
 * last moment, on the request sent on the wire. The URI known to {@code RestClient} — the one it
 * puts in exception messages, logs and observations — never contains the key.
 */
final class ApiTokenInterceptor implements ClientHttpRequestInterceptor {

  private final String encodedKey;

  ApiTokenInterceptor(String apiKey) {
    this.encodedKey = UriUtils.encodeQueryParam(apiKey, StandardCharsets.UTF_8);
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    URI withKey =
        UriComponentsBuilder.fromUri(request.getURI())
            .queryParam("api_token", encodedKey)
            .build(true)
            .toUri();
    return execution.execute(new KeyedRequest(request, withKey), body);
  }

  @Override
  public String toString() {
    return "ApiTokenInterceptor[<redacted>]";
  }

  /** The original request, sent to another URI. */
  private static final class KeyedRequest extends HttpRequestWrapper {
    private final URI uri;

    KeyedRequest(HttpRequest request, URI uri) {
      super(request);
      this.uri = uri;
    }

    @Override
    public URI getURI() {
      return uri;
    }
  }
}
