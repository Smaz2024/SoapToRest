package com.example;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Represents the data transfer object (DTO) received from the downstream REST service.
 *
 * <p>This class defines the JSON structure that the gateway expects to parse from the external REST
 * endpoint's response. The {@code @Data} annotation from Lombok automatically generates standard
 * boilerplate code like getters, setters, {@code toString()}, {@code equals()}, and {@code
 * hashCode()}.
 */
@Data
public class RestResponse {

  /** The outcome of the transaction, typically a string like "APPROVED" or "DECLINED". */
  private String status;

  /**
   * The monetary value of the transaction after any currency conversion has been applied. Using
   * {@link BigDecimal} is recommended for financial calculations to avoid floating-point
   * inaccuracies.
   */
  private BigDecimal convertedAmount;
}
