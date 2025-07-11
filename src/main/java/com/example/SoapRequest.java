package com.example;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Represents the data transfer object (DTO) for an incoming SOAP request.
 *
 * <p>This class defines the structure and validation rules for the data expected in the body of a
 * SOAP request. The {@code @Data} annotation from Lombok automatically generates standard
 * boilerplate code like getters, setters, {@code toString()}, {@code equals()}, and {@code
 * hashCode()}.
 */
@Data
public class SoapRequest {

  /**
   * The unique identifier for the transaction.
   *
   * <p>This field is mandatory and cannot be null or consist only of whitespace.
   */
  @NotBlank(message = "Transaction ID is required")
  private String transactionId;

  /**
   * The three-letter ISO 4217 currency code for the transaction (e.g., "USD", "EUR").
   *
   * <p>This field is mandatory and must be exactly 3 uppercase alphabetic characters.
   */
  @NotNull(message = "Currency is required")
  @Size(min = 3, max = 3, message = "Currency must be 3 characters")
  @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase ISO code")
  private String currency;
}
