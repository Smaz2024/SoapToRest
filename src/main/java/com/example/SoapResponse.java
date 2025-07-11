package com.example;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Represents the data transfer object (DTO) for an outgoing SOAP response.
 *
 * <p>This class defines the structure of the data that will be serialized into the body of the SOAP
 * response sent back to the client. The {@code @Data} annotation from Lombok automatically
 * generates standard boilerplate code like getters, setters, {@code toString()}, {@code equals()},
 * and {@code hashCode()}.
 */
@Data
public class SoapResponse {

  /**
   * The final status of the transaction (e.g., "APPROVED", "DECLINED", "SERVICE_UNAVAILABLE"). This
   * field provides a clear, human-readable outcome of the request.
   */
  private String status;

  /**
   * The monetary value of the transaction after any processing or currency conversion has been
   * applied.
   *
   * <p>Using {@link BigDecimal} is crucial for financial calculations to avoid floating-point
   * inaccuracies. This field may be null in certain scenarios, such as a service failure fallback.
   */
  private BigDecimal convertedAmount;
}
