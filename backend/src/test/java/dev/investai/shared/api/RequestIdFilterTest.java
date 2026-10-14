package dev.investai.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(OutputCaptureExtension.class)
class RequestIdFilterTest {

  private static final String HEADER = "X-Request-Id";
  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  private final RequestIdFilter filter = new RequestIdFilter();
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final AtomicReference<String> requestIdSeenByChain = new AtomicReference<>();
  private final FilterChain chain = (req, res) -> requestIdSeenByChain.set(MDC.get("requestId"));

  @Test
  void generatesRequestIdWhenHeaderIsAbsent() throws Exception {
    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(HEADER)).matches(UUID_PATTERN);
    assertThat(requestIdSeenByChain.get()).isEqualTo(response.getHeader(HEADER));
    assertThat(request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE))
        .isEqualTo(response.getHeader(HEADER));
  }

  @Test
  void ignoresRequestIdChosenByTheClient() throws Exception {
    request.addHeader(HEADER, "abc\nFAKE LOG LINE");

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(HEADER)).matches(UUID_PATTERN);
    assertThat(requestIdSeenByChain.get()).isEqualTo(response.getHeader(HEADER));
  }

  @Test
  void generatesDistinctIdsPerRequest() throws Exception {
    var otherResponse = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);
    filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/x"), otherResponse, chain);

    assertThat(otherResponse.getHeader(HEADER)).isNotEqualTo(response.getHeader(HEADER));
  }

  @Test
  void clearsMdcAfterTheRequest() throws Exception {
    filter.doFilter(request, response, chain);

    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void logsServerErrorAndClearsMdcWhenTheChainFails(CapturedOutput output) {
    FilterChain failingChain =
        (req, res) -> {
          throw new IllegalStateException("boom");
        };

    assertThatThrownBy(() -> filter.doFilter(request, response, failingChain))
        .isInstanceOf(IllegalStateException.class);
    assertThat(output).contains("GET UNMATCHED -> 500");
    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void logsRouteTemplateInsteadOfRawUrl(CapturedOutput output) throws Exception {
    var withIdAndQuery = new MockHttpServletRequest("GET", "/api/v1/things/42");
    withIdAndQuery.setQueryString("token=secret-value");
    withIdAndQuery.setAttribute(
        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/things/{id}");
    FilterChain noContent = (req, res) -> response.setStatus(204);

    filter.doFilter(withIdAndQuery, response, noContent);

    assertThat(output).contains("GET /api/v1/things/{id} -> 204");
    assertThat(output).doesNotContain("secret-value").doesNotContain("/things/42");
  }

  @Test
  void logsUnmatchedWhenNoRouteWasSelected(CapturedOutput output) throws Exception {
    filter.doFilter(request, response, chain);

    assertThat(output).contains("GET UNMATCHED -> 200");
  }
}
