package dev.investai.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Documentation of the problem details produced by {@link ApiExceptionHandler} (RFC 9457 +
 * ADR-0006) for the OpenAPI contract; responses are built as {@link
 * org.springframework.http.ProblemDetail}.
 *
 * @param type problem type, {@code about:blank} for now
 * @param title HTTP reason phrase
 * @param status HTTP status code
 * @param instance request path
 * @param code stable machine-readable code, e.g. {@code auth.required}
 * @param requestId identifier of the request, also in the {@code X-Request-Id} header and the logs;
 *     always present over HTTP, set by {@link RequestIdFilter} before any other filter
 * @param errors field violations, for {@code validation.failed} only
 */
@Schema(name = "Problem")
record ProblemSchema(
    @Nullable URI type,
    String title,
    int status,
    @Nullable URI instance,
    String code,
    String requestId,
    @Nullable List<FieldViolation> errors) {}
