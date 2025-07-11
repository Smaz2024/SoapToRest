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

  @Inject @RestClient private RestServiceClient restClient;
  @Inject private Validator validator;
  @Resource private ManagedExecutorService managedExecutor;

  @Inject
  @ConfigProperty(name = "com.example.gateway.request.timeout", defaultValue = "5000")
  private Long requestTimeout;

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
    final Span span = tracer.spanBuilder("process-soap-request").startSpan();
    span.setAttribute("transaction.id", request.getTransactionId());
    log.info("Starting processing for transaction id: {}", request.getTransactionId());

    try (Scope scope = span.makeCurrent()) {
      validate(request);

      // This CompletionStage represents the async pipeline.
      CompletionStage<SoapResponse> asyncProcessingChain =
          CompletableFuture.supplyAsync(() -> request, managedExecutor)
              .thenApply(this::convertToRestRequest)
              .thenCompose(this::callRestService)
              .thenApply(this::convertToSoapResponse);

      // Block and wait for the asynchronous pipeline to complete.
      return asyncProcessingChain.toCompletableFuture().get(requestTimeout, TimeUnit.MILLISECONDS);

    } catch (ConstraintViolationException e) {
      log.error(
          "Validation failed for transaction {}: {}", request.getTransactionId(), e.getMessage());
      span.recordException(e);
      span.setStatus(StatusCode.ERROR, "Validation failed");
      throw new WebServiceException(
          "Validation failed: " + e.getMessage(),
          new GatewayFault("Validation failed: " + e.getMessage()));
    } catch (TimeoutException e) {
      log.error("Request timed out for transaction {}", request.getTransactionId(), e);
      span.recordException(e);
      span.setStatus(StatusCode.ERROR, "Request timed out");
      throw new WebServiceException("Request timed out", e);
    } catch (ExecutionException | InterruptedException e) {
      log.error(
          "Error processing transaction {}",
          request.getTransactionId(),
          e.getCause() != null ? e.getCause() : e);
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      span.recordException(cause);
      span.setStatus(StatusCode.ERROR, "Request processing failed");
      // Propagate as a standard exception that JAX-WS will turn into a SOAP Fault.
      throw new WebServiceException("Request processing failed", cause);
    } finally {
      span.end();
    }
  }

  /**
   * A fallback method invoked by MicroProfile Fault Tolerance if {@code processRequest} fails. The
   * signature is now synchronous to match the main method.
   *
   * @param request The original request that caused the failure.
   * @return A default fallback response.
   */
  public SoapResponse processRequestFallback(SoapRequest request) {
    log.warn("Fallback initiated for transaction id: {}", request.getTransactionId());
    SoapResponse fallbackResponse = new SoapResponse();
    fallbackResponse.setStatus("SERVICE_UNAVAILABLE");
    return fallbackResponse;
  }

  private void validate(SoapRequest request) {
    log.info("Validating request for transaction id: {}", request.getTransactionId());
    Set<ConstraintViolation<SoapRequest>> violations = validator.validate(request);
    if (!violations.isEmpty()) {
      String errorMessages =
          violations.stream()
              .map(v -> v.getPropertyPath() + " " + v.getMessage())
              .collect(Collectors.joining(", "));
      throw new ConstraintViolationException(errorMessages, violations);
    }
  }

  private RestRequest convertToRestRequest(SoapRequest request) {
    log.debug("Converting SOAP request to REST for transaction id: {}", request.getTransactionId());
    RestRequest restRequest = new RestRequest();
    restRequest.setTransactionId(request.getTransactionId());
    restRequest.setCurrency(request.getCurrency());
    return restRequest;
  }

  private CompletionStage<RestResponse> callRestService(RestRequest restRequest) {
    log.info(
        "Calling downstream REST service for transaction id: {}", restRequest.getTransactionId());
    return restClient.callRestService(restRequest);
  }

  private SoapResponse convertToSoapResponse(RestResponse restResponse) {
    log.debug("Converting REST response to SOAP for status: {}", restResponse.getStatus());
    SoapResponse soapResponse = new SoapResponse();
    soapResponse.setStatus(restResponse.getStatus());
    soapResponse.setConvertedAmount(restResponse.getConvertedAmount());
    return soapResponse;
  }
}
