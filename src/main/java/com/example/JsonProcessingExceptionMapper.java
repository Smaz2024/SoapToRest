package com.example;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * A JAX-RS ExceptionMapper that catches {@link JsonbException}s, which typically occur during JSON
 * serialization or deserialization errors.
 *
 * <p>When a JSON binding error happens (e.g., malformed JSON, type mismatch), this mapper
 * intercepts the exception and translates it into a user-friendly HTTP 400 (Bad Request) response.
 * It also creates a dedicated OpenTelemetry span to trace the error, providing observability into
 * JSON processing failures.
 *
 * <p>The {@link Provider} annotation registers this class with the JAX-RS runtime so it can
 * automatically handle the specified exceptions.
 */
@Provider
public class JsonProcessingExceptionMapper implements ExceptionMapper<JsonbException> {

  /** The OpenTelemetry tracer, injected to create custom telemetry spans for error tracing. */
  // @Inject private Tracer tracer;

  // Using direct OpenTelemetry access instead of CDI injection to avoid:
  // - DeploymentException: WELD-001408 (Unsatisfied dependencies)
  // - Works with Liberty's mpTelemetry feature initialization timing
  // - Safe no-op fallback if telemetry not configured
  private final Tracer tracer = GlobalOpenTelemetry.getTracer(GatewayServiceImpl.class.getName());

  /**
   * Maps a {@link JsonbException} to a standard HTTP {@link Response}.
   *
   * @param ex The {@link JsonbException} that was thrown by the JAX-RS runtime.
   * @return A {@link Response} object with a 400 (Bad Request) status code and a generic error
   *     message in the entity body.
   */
  @Override
  public Response toResponse(JsonbException ex) {
    // Start a new span to specifically trace this JSON processing failure.
    final Span span = tracer.spanBuilder("json-processing-exception").startSpan();
    try {
      // Record the exception details and set the span status to ERROR.
      span.recordException(ex);
      span.setStatus(StatusCode.ERROR, "JSON processing failed");

      // Build a standard 400 Bad Request response to send to the client.
      // We avoid sending the raw exception message to prevent leaking internal details.
      return Response.status(Response.Status.BAD_REQUEST).entity("Invalid JSON format").build();
    } finally {
      // Ensure the span is always closed, regardless of any errors in the try block.
      span.end();
    }
  }
}
