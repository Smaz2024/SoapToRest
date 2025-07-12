package com.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import jakarta.xml.ws.WebServiceException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GatewayServiceImplTest {

  // The class under test. Mocks will be injected into this instance.
  @InjectMocks private GatewayServiceImpl gatewayService;

  // Mock for the downstream REST client.
  @Mock @RestClient private RestServiceClient restClient;

  // Mock for the bean validator.
  @Mock private Validator validator;

  // Mock for the managed executor service.
  @Mock private ManagedExecutorService managedExecutor;

  private SoapRequest validSoapRequest;

  @BeforeEach
  void setUp() throws Exception {
    // To test the asynchronous logic predictably, we configure the mock executor
    // to run any submitted task immediately on the same thread. This allows us
    // to test the CompletableFuture chain deterministically.
    doAnswer(
            invocation -> {
              Runnable task = invocation.getArgument(0);
              task.run();
              return null; // void method
            })
        .when(managedExecutor)
        .execute(any(Runnable.class));

    // Set the private 'requestTimeout' field using reflection, as it's normally
    // injected by MicroProfile Config.
    setField(gatewayService, "requestTimeout", 5000L);

    // Prepare a standard valid request object for use in multiple tests.
    validSoapRequest = new SoapRequest();
    validSoapRequest.setTransactionId("tx-12345");
    validSoapRequest.setCurrency("USD");
  }

  // Helper method to set private fields on the test subject.
  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  @Test
  @DisplayName("processRequest should return a successful response for a valid request")
  void processRequest_shouldReturnSuccessResponse_whenRequestIsValid() {
    // Arrange
    // 1. Mock validation to pass (return no violations).
    when(validator.validate(any(SoapRequest.class))).thenReturn(Collections.emptySet());

    // 2. Define the expected response from the downstream REST service.
    RestResponse restResponse = new RestResponse();
    restResponse.setStatus("APPROVED");
    restResponse.setConvertedAmount(new BigDecimal("123.45"));
    CompletableFuture<RestResponse> completedFuture =
        CompletableFuture.completedFuture(restResponse);

    // 3. Mock the REST client to return the successful response.
    when(restClient.callRestService(any(RestRequest.class))).thenReturn(completedFuture);

    // Act
    SoapResponse soapResponse = gatewayService.processRequest(validSoapRequest);

    // Assert
    // 1. Verify the final SOAP response is correctly mapped from the REST response.
    assertNotNull(soapResponse);
    assertEquals("APPROVED", soapResponse.getStatus());
    assertEquals(new BigDecimal("123.45"), soapResponse.getConvertedAmount());

    // 2. Verify that validation and the REST client were called exactly once.
    verify(validator, times(1)).validate(validSoapRequest);
    verify(restClient, times(1)).callRestService(any(RestRequest.class));
  }

  @Test
  @DisplayName("processRequest should throw WebServiceException on validation failure")
  void processRequest_shouldThrowWebServiceException_whenValidationFails() {
    // Arrange
    // 1. Mock a constraint violation to simulate a validation error.
    ConstraintViolation<SoapRequest> violation = mock(ConstraintViolation.class);
    Path propertyPath = mock(Path.class);
    when(propertyPath.toString()).thenReturn("transactionId");
    when(violation.getPropertyPath()).thenReturn(propertyPath);
    when(violation.getMessage()).thenReturn("is required");
    Set<ConstraintViolation<SoapRequest>> violations = new HashSet<>(Set.of(violation));

    // 2. Mock the validator to return the set of violations.
    when(validator.validate(any(SoapRequest.class)))
        .thenThrow(
            new ConstraintViolationException(
                "Validation failed: transactionId is required", violations));

    // Act & Assert
    // 1. Expect a WebServiceException to be thrown.
    WebServiceException exception =
        assertThrows(
            WebServiceException.class,
            () -> gatewayService.processRequest(validSoapRequest),
            "Expected WebServiceException for validation failure");

    // 2. Check the exception message for correctness.
    assertTrue(exception.getMessage().contains("Validation failed: transactionId is required"));

    // 3. Verify that the downstream REST client was never called.
    verify(restClient, never()).callRestService(any(RestRequest.class));
  }

  @Test
  @DisplayName("processRequest should throw WebServiceException when the REST call fails")
  void processRequest_shouldThrowWebServiceException_whenRestCallFails() {
    // Arrange
    // 1. Mock validation to pass.
    when(validator.validate(any(SoapRequest.class))).thenReturn(Collections.emptySet());

    // 2. Mock the REST client to return a failed future, simulating a downstream error.
    RuntimeException downstreamException = new RuntimeException("Downstream service unavailable");
    CompletableFuture<RestResponse> failedFuture =
        CompletableFuture.failedFuture(downstreamException);
    when(restClient.callRestService(any(RestRequest.class))).thenReturn(failedFuture);

    // Act & Assert
    // 1. Expect a WebServiceException.
    WebServiceException exception =
        assertThrows(
            WebServiceException.class,
            () -> gatewayService.processRequest(validSoapRequest),
            "Expected WebServiceException for downstream failure");

    // 2. The original exception is wrapped in ExecutionException by CompletableFuture.
    //    Verify that the cause of the WebServiceException is the original downstream exception.
    assertNotNull(exception.getCause());
    assertEquals(downstreamException, exception.getCause().getCause());
    assertEquals("Request processing failed", exception.getMessage());
  }

  @Test
  @DisplayName("processRequest should throw WebServiceException on timeout")
  void processRequest_shouldThrowWebServiceException_whenRequestTimesOut() throws Exception {
    // Arrange
    // 1. Set a very short timeout for the test.
    setField(gatewayService, "requestTimeout", 10L);

    // 2. Mock validation to pass.
    when(validator.validate(any(SoapRequest.class))).thenReturn(Collections.emptySet());

    // 3. Mock the REST client to return a future that never completes, simulating a timeout.
    when(restClient.callRestService(any(RestRequest.class))).thenReturn(new CompletableFuture<>());

    // Act & Assert
    // 1. Expect a WebServiceException.
    WebServiceException exception =
        assertThrows(
            WebServiceException.class,
            () -> gatewayService.processRequest(validSoapRequest),
            "Expected WebServiceException for timeout");

    // 2. Verify the cause is a TimeoutException.
    assertNotNull(exception.getCause());
    assertInstanceOf(TimeoutException.class, exception.getCause());
    assertEquals("Request timed out", exception.getMessage());
  }

  @Test
  @DisplayName("processRequestFallback should return a service unavailable response")
  void processRequestFallback_shouldReturnUnavailableStatus() {
    // Arrange (The fallback method is simple and has no external dependencies)

    // Act
    SoapResponse fallbackResponse = gatewayService.processRequestFallback(validSoapRequest);

    // Assert
    assertNotNull(fallbackResponse);
    assertEquals("SERVICE_UNAVAILABLE", fallbackResponse.getStatus());
    assertNull(fallbackResponse.getConvertedAmount());
  }
}
