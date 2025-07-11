package com.example;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;

/**
 * Represents the structured data payload for a {@link GatewayFault}.
 *
 * <p>This class acts as the "fault bean" for the JAX-WS {@code WebFault}. When a {@code
 * GatewayFault} is thrown, JAX-WS serializes an instance of this class into the {@code <detail>}
 * element of the resulting SOAP Fault.
 *
 * <p>The {@code @Data} annotation from Lombok automatically generates standard boilerplate code
 * like getters, setters, {@code toString()}, {@code equals()}, and {@code hashCode()}. The
 * {@code @XmlRootElement} and {@code @XmlAccessorType} annotations are used by JAXB to correctly
 * marshal this object into XML.
 */
@Data
@XmlRootElement(name = "GatewayFaultInfo")
@XmlAccessorType(XmlAccessType.FIELD)
public class GatewayFaultInfo {

  /** A human-readable message describing the error that occurred. */
  private String errorMessage;

  /** A specific error code that can be used by clients for programmatic error handling. */
  private String errorCode;

  /** The UTC timestamp indicating when the error occurred, in ISO-8601 format. */
  private String timestamp;

  /**
   * Constructs a new GatewayFaultInfo instance and initializes the timestamp to the current time.
   */
  public GatewayFaultInfo() {
    this.timestamp = java.time.Instant.now().toString();
  }
}
