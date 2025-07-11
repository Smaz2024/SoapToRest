package com.example;

import jakarta.xml.ws.WebFault;

/**
 * Custom exception class for the SOAP gateway service, designed to be translated into a standard
 * SOAP Fault.
 *
 * <p>The {@link WebFault} annotation specifies the details of the SOAP Fault element, including the
 * name and the "fault bean" ({@link GatewayFaultInfo}) that carries the structured error details.
 * This ensures that when this exception is thrown from a JAX-WS endpoint, it is properly serialized
 * into the SOAP response.
 */
@WebFault(name = "GatewayFault", faultBean = "com.example.GatewayFaultInfo")
public class GatewayFault extends Exception {

  /** The fault bean that holds the structured error details for the SOAP Fault. */
  private GatewayFaultInfo faultInfo;

  /**
   * Constructs a new GatewayFault with the specified detail message. The fault info bean is
   * automatically created and populated with the message.
   *
   * @param message the detail message (which is saved for later retrieval by the {@link
   *     #getMessage()} method).
   */
  public GatewayFault(String message) {
    super(message);
    this.faultInfo = new GatewayFaultInfo();
    this.faultInfo.setErrorMessage(message);
  }

  /**
   * Constructs a new GatewayFault with the specified detail message and cause. The fault info bean
   * is automatically created and populated with the message.
   *
   * @param message the detail message (which is saved for later retrieval by the {@link
   *     #getMessage()} method).
   * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
   *     A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.
   */
  public GatewayFault(String message, Throwable cause) {
    super(message, cause);
    this.faultInfo = new GatewayFaultInfo();
    this.faultInfo.setErrorMessage(message);
  }

  /**
   * Gets the fault bean containing the detailed error information.
   *
   * @return the fault info bean.
   */
  public GatewayFaultInfo getFaultInfo() {
    return faultInfo;
  }

  /**
   * Sets the fault bean containing the detailed error information.
   *
   * @param faultInfo the fault info bean to set.
   */
  public void setFaultInfo(GatewayFaultInfo faultInfo) {
    this.faultInfo = faultInfo;
  }
}
