package com.example;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;

// =================================================================================
// 1. SOAP Service Interface (SEI) - CORRECTED
// =================================================================================
/**
 * Defines the service endpoint interface (SEI) for the SOAP gateway. The method signature is now
 * synchronous, returning a concrete SoapResponse type to ensure compatibility with JAXB for WSDL
 * generation and data binding.
 */
@WebService(name = "GatewayService", targetNamespace = "http://example.com/gateway/v1")
@SOAPBinding(
    style = SOAPBinding.Style.DOCUMENT,
    use = SOAPBinding.Use.LITERAL,
    parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
public interface GatewayService {

  @WebMethod
  SoapResponse processRequest(@WebParam(name = "request") SoapRequest request);
}
