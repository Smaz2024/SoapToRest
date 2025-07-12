package com.example;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jws.WebService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.xml.ws.WebServiceException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the {@link GatewayService} interface.
 *
 * <p>This implementation now correctly adheres to the synchronous interface contract required by
 * JAX-WS and JAXB. While the internal logic still leverages an efficient asynchronous pipeline, the
 * main {@code processRequest} method blocks at the end to await the result, ensuring a concrete
 * object is returned.
 */
@WebService(
    serviceName = "GatewayService",
    portName = "GatewayPort",
    endpointInterface = "com.example.GatewayService",
    targetNamespace = "http://example.com/gateway/v1")
@ApplicationScoped
@PermitAll
public class GatewayServiceImpl implements GatewayService {

  private static final Logger log = LoggerFactory.getLogger(GatewayServiceImpl.class);

  /** The type-safe MicroProfile Rest Client for the downstream service. */
  @Inject @RestClient private RestServiceClient restClient;

  /** The Jakarta Bean Validation validator, used to enforce constraints on the SOAP request. */
  @Inject private Validator validator;

  /**
   * The container-managed executor service, used to run asynchronous tasks within the application
   * server's managed thread pool. This is the preferred way to handle concurrency in Jakarta EE.
   */
  @Resource private ManagedExecutorService managedExecutor;

  /**
   * The maximum time to wait for the entire asynchronous processing chain to complete, configured
   * via MicroProfile Config.
   */
  @Inject
  @ConfigProperty(name = "com.example.gateway.request.timeout", defaultValue = "5000")
  private Long requestTimeout;

  /**
   * The OpenTelemetry tracer, used for creating custom spans to trace the execution flow. Using
   * global access avoids CDI injection issues with telemetry initialization.
   */
  private final Tracer tracer = GlobalOpenTelemetry.getTracer(GatewayServiceImpl.class.getName());

  /**
   * Processes a SOAP request by orchestrating validation, conversion, and a downstream REST call.
   *
   * <p>This method adheres to the synchronous contract of the {@link GatewayService} interface. It
   * executes the core logic asynchronously for performance but blocks to await the final result
   * before returning, thus satisfying JAXB's requirement for concrete types.
   *
   * @param request The incoming SOAP request.
   * @return The corresponding {@link SoapResponse}.
   * @throws WebServiceException if any processing error occurs.
   */
  @Override
  @Timeout(5000)
  @Fallback(fallbackMethod = "processRequestFallback")
  @Timed(
      name = "gateway_process_time",
      description = "Measures the time to process a gateway request.")
  @Counted(
      name = "gateway_request_count",
      description = "Counts the total number of gateway requests.")
  public SoapResponse processRequest(SoapRequest request) {
    // Start a new OpenTelemetry span to trace this entire request processing flow.
    final Span span = tracer.spanBuilder("process-soap-request").startSpan();
    span.setAttribute("transaction.id", request.getTransactionId());
    log.info("Starting processing for transaction id: {}", request.getTransactionId());

    // Use try-with-resources to ensure the span's scope is always closed correctly.
    try (Scope scope = span.makeCurrent()) {
      // Step 1: Perform synchronous validation on the incoming request.
      validate(request);

      // Step 2: Define the asynchronous processing pipeline.
      // This chain of operations runs on the managedExecutor thread pool.
      CompletionStage<SoapResponse> asyncProcessingChain =
          CompletableFuture.supplyAsync(() -> request, managedExecutor)
              .thenApply(this::convertToRestRequest) // Convert SOAP request to REST DTO
              .thenCompose(this::callRestService) // Asynchronously call the downstream service
              .thenApply(this::convertToSoapResponse); // Convert REST response back to SOAP DTO

      // Step 3: Block and wait for the asynchronous pipeline to complete.
      // This is the bridge between the async internal logic and the sync JAX-WS contract.
      // A timeout is applied to prevent indefinite blocking.
      return asyncProcessingChain.toCompletableFuture().get(requestTimeout, TimeUnit.MILLISECONDS);

    } catch (ConstraintViolationException e) {
      // Handle validation failures by creating a specific SOAP Fault.
      log.error(
          "Validation failed for transaction {}: {}", request.getTransactionId(), e.getMessage());
      span.recordException(e);
      span.setStatus(StatusCode.ERROR, "Validation failed");
      // JAX-WS will convert this WebServiceException into a standard SOAP Fault.
      // We embed a custom GatewayFault for more detailed error reporting.
      throw new WebServiceException(
          "Validation failed: " + e.getMessage(),
          new GatewayFault("Validation failed: " + e.getMessage()));
    } catch (TimeoutException e) {
      // Handle timeouts from the blocking .get() call.
      log.error("Request timed out for transaction {}", request.getTransactionId(), e);
      span.recordException(e);
      span.setStatus(StatusCode.ERROR, "Request timed out");
      throw new WebServiceException("Request timed out", e);
    } catch (ExecutionException | InterruptedException e) {
      // Handle exceptions thrown from within the asynchronous pipeline.
      log.error(
          "Error processing transaction {}",
          request.getTransactionId(),
          e.getCause() != null ? e.getCause() : e);
      // Unwrap the actual cause from the ExecutionException for better error reporting.
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      span.recordException(cause);
      span.setStatus(StatusCode.ERROR, "Request processing failed");
      // Propagate as a standard exception that JAX-WS will turn into a SOAP Fault.
      throw new WebServiceException("Request processing failed", cause);
    } finally {
      // Ensure the span is always ended, marking the completion of the trace.
      span.end();
    }
  }

  /**
   * A fallback method invoked by MicroProfile Fault Tolerance if {@code processRequest} fails after
   * all other policies (like Timeout) have been exhausted. The signature is synchronous to match
   * the main method.
   *
   * @param request The original request that caused the failure.
   * @return A default {@link SoapResponse} indicating the service is unavailable.
   */
  public SoapResponse processRequestFallback(SoapRequest request) {
    log.warn("Fallback initiated for transaction id: {}", request.getTransactionId());
    SoapResponse fallbackResponse = new SoapResponse();
    fallbackResponse.setStatus("SERVICE_UNAVAILABLE");
    return fallbackResponse;
  }

  /**
   * Validates the incoming {@link SoapRequest} using Jakarta Bean Validation.
   *
   * @param request The request object to validate.
   * @throws ConstraintViolationException if validation fails.
   */
  private void validate(SoapRequest request) {
    log.info("Validating request for transaction id: {}", request.getTransactionId());
    Set<ConstraintViolation<SoapRequest>> violations = validator.validate(request);
    if (!violations.isEmpty()) {
      // Collect all validation failure messages into a single string.
      String errorMessages =
          violations.stream()
              .map(v -> v.getPropertyPath() + " " + v.getMessage())
              .collect(Collectors.joining(", "));
      throw new ConstraintViolationException(errorMessages, violations);
    }
  }

  /**
   * Converts a {@link SoapRequest} DTO into a {@link RestRequest} DTO.
   *
   * @param request The incoming SOAP request.
   * @return The corresponding REST request for the downstream service.
   */
  private RestRequest convertToRestRequest(SoapRequest request) {
    log.debug("Converting SOAP request to REST for transaction id: {}", request.getTransactionId());
    RestRequest restRequest = new RestRequest();
    restRequest.setTransactionId(request.getTransactionId());
    restRequest.setCurrency(request.getCurrency());
    // Note: The 'amount' field is not mapped here as it's not in the SoapRequest.
    // It would be set here if it were part of the incoming data.
    return restRequest;
  }

  /**
   * Calls the downstream REST service via the MicroProfile Rest Client.
   *
   * @param restRequest The request to send to the downstream service.
   * @return A {@link CompletionStage} that will complete with the {@link RestResponse}.
   */
  private CompletionStage<RestResponse> callRestService(RestRequest restRequest) {
    log.info(
        "Calling downstream REST service for transaction id: {}", restRequest.getTransactionId());
    // The restClient is asynchronous and already protected by Fault Tolerance policies.
    return restClient.callRestService(restRequest);
  }

  /**
   * Converts a {@link RestResponse} DTO from the downstream service into a {@link SoapResponse}
   * DTO.
   *
   * @param restResponse The response received from the REST service.
   * @return The corresponding SOAP response to be sent back to the original client.
   */
  private SoapResponse convertToSoapResponse(RestResponse restResponse) {
    log.debug("Converting REST response to SOAP for status: {}", restResponse.getStatus());
    SoapResponse soapResponse = new SoapResponse();
    soapResponse.setStatus(restResponse.getStatus());
    soapResponse.setConvertedAmount(restResponse.getConvertedAmount());
    return soapResponse;
  }
}
