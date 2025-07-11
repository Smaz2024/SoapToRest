package com.example;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Represents the data transfer object (DTO) sent to the downstream REST service.
 *
 * <p>This class defines the JSON payload structure that the gateway will send when it calls the
 * external REST endpoint. The {@code @Data} annotation from Lombok automatically generates standard
 * boilerplate code like getters, setters, {@code toString()}, {@code equals()}, and {@code
 * hashCode()}.
 */
@Data
public class RestRequest {

  /** The unique identifier for the transaction, used for tracking and correlation. */
  private String transactionId;

  /** The three-letter ISO 4217 currency code for the transaction (e.g., "USD", "EUR"). */
  private String currency;

  /**
   * The monetary value of the transaction. Using {@link BigDecimal} is recommended for financial
   * calculations to avoid floating-point inaccuracies.
   */
  private BigDecimal amount;
}
