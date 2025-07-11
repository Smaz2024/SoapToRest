package com.example;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletionStage;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Defines a type-safe, resilient client for a downstream REST service using MicroProfile Rest
 * Client.
 *
 * <p>This interface uses a combination of JAX-RS and MicroProfile annotations to declare how to
 * connect to, interact with, and protect against failures from the remote endpoint. @Path("/v1") A
 * standard JAX-RS annotation that defines the base URI path for all methods in this client. All
 * method paths will be relative to this one. @RegisterRestClient(configKey = "rest-service") A
 * MicroProfile annotation that marks this interface for CDI injection as a REST client. The {@code
 * configKey} property links this client to configuration properties (like the base URL) in {@code
 * microprofile-config.properties}, for example: {@code
 * com.example.RestServiceClient/mp-rest/url=http://downstream-service:8080} @RegisterProvider(JsonProcessingExceptionMapper.class)
 * Registers a custom JAX-RS provider specifically for this client. In this case, it registers an
 * {@code ExceptionMapper} to provide custom handling for JSON binding errors, ensuring consistent
 * error responses.
 */
@Path("/v1")
@RegisterRestClient(configKey = "rest-service")
@RegisterProvider(JsonProcessingExceptionMapper.class)
public interface RestServiceClient {

  /**
   * Asynchronously calls the downstream REST service's {@code /process} endpoint with a full suite
   * of fault tolerance policies.
   *
   * <p>This method is heavily decorated with MicroProfile Fault Tolerance annotations to create a
   * robust, resilient client. The policies are applied in a specific order (e.g., Timeout, Retry,
   * CircuitBreaker, Bulkhead).
   *
   * @param request The {@link RestRequest} object to be serialized into the JSON request body.
   * @return A {@link CompletionStage} that will eventually complete with the {@link RestResponse}
   *     from the downstream service or fail if all fault tolerance policies are
   *     exhausted. @Asynchronous Enables the method to be executed on a separate thread, returning
   *     a {@code CompletionStage} immediately. This is a prerequisite for applying other fault
   *     tolerance policies to a non-blocking operation. @Timeout(5000) Sets a 5-second (5000ms)
   *     execution time limit for the operation. If the downstream service does not respond within
   *     this window, a {@link TimeoutException} is thrown, which can then trigger a
   *     retry. @Retry(...) Configures a retry policy for transient failures. - {@code maxRetries =
   *     3}: If the initial call fails, it will be attempted up to 3 more times. - {@code delay =
   *     500}: A 500ms pause is introduced between each retry attempt to avoid overwhelming the
   *     downstream service. - {@code retryOn}: Specifies the exact exceptions that should trigger a
   *     retry. This is crucial for retrying only on network-related or transient server
   *     errors. @CircuitBreaker(requestVolumeThreshold = 10) Implements the Circuit Breaker pattern
   *     to prevent repeated calls to a failing service. - {@code requestVolumeThreshold = 10}: The
   *     breaker monitors the last 10 calls. If 50% (by default) of them fail, the circuit "opens".
   *     - While open, all subsequent calls fail immediately without being sent, protecting the
   *     system. - After a delay (default 5s), the circuit becomes "half-open," allowing one test
   *     call. If it succeeds, the circuit closes; otherwise, it opens again. @Bulkhead(15) Limits
   *     the number of concurrent requests to this method to 15. If a 16th request arrives while 15
   *     are in-flight, it will be rejected immediately. This prevents the client from overwhelming
   *     the downstream service during traffic spikes. @Fallback(fallbackMethod =
   *     "callRestServiceFallback") The final line of defense. If the method ultimately fails after
   *     all retries and circuit breaker checks, this policy invokes the specified fallback method
   *     to provide a default or graceful error response.
   */
  @POST
  @Path("/process")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Asynchronous
  @Timeout(5000)
  @Retry(
      maxRetries = 3,
      delay = 500,
      retryOn = {
        TimeoutException.class,
        WebApplicationException.class,
        ConnectException.class,
        SocketTimeoutException.class
      })
  @CircuitBreaker(requestVolumeThreshold = 10)
  @Bulkhead(15)
  @Fallback(fallbackMethod = "callRestServiceFallback")
  CompletionStage<RestResponse> callRestService(RestRequest request);

  /**
   * Provides a default fallback response when the primary {@code callRestService} method fails.
   *
   * <p>This method is invoked by the MicroProfile Fault Tolerance framework. It returns a completed
   * future with a predefined "SERVICE_UNAVAILABLE" status, ensuring that the calling service
   * receives a graceful error response instead of an unhandled exception.
   *
   * @param request The original request that was sent to the failed primary method.
   * @return A {@link CompletionStage} containing a default {@link RestResponse}.
   */
  default CompletionStage<RestResponse> callRestServiceFallback(RestRequest request) {
    RestResponse fallbackResponse = new RestResponse();
    fallbackResponse.setStatus("SERVICE_UNAVAILABLE");
    return java.util.concurrent.CompletableFuture.completedFuture(fallbackResponse);
  }
}
