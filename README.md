# SOAP to REST Gateway

This project is a high-performance, resilient gateway application that translates incoming SOAP 1.1 web service requests into modern RESTful API calls. It is built using Java 21 on the Open Liberty runtime, leveraging the power of Jakarta EE 10 and Eclipse MicroProfile 6.0.

The primary goal is to provide a compatibility layer for legacy clients that can only communicate via SOAP, allowing them to interact with a backend system that exposes a RESTful interface.

## Core Features

- **Frameworks**: Built on Open Liberty, a lightweight and flexible Java runtime.
- **Web Services**:
  - **SOAP Endpoint**: Exposes a standard JAX-WS 4.0 endpoint for legacy clients.
  - **REST Client**: Uses the type-safe MicroProfile Rest Client 3.0 to communicate with the downstream service.
- **Resilience & Fault Tolerance**: Implements robust error handling using MicroProfile Fault Tolerance 4.0:
  - `@Retry`: Automatically retries failed calls to the downstream service.
  - `@Timeout`: Prevents long-running requests from blocking resources.
  - `@Fallback`: Provides a graceful degradation path when all else fails.
  - `@CircuitBreaker`: Stops requests to a failing service to allow it to recover.
  - `@Bulkhead`: Limits concurrent requests to prevent system overload.
- **Asynchronous Processing**: The core logic uses `CompletableFuture` and a Jakarta `ManagedExecutorService` to process requests asynchronously, improving throughput and resource utilization.
- **Observability**:
  - **Distributed Tracing**: Integrated with OpenTelemetry via `mpTelemetry-1.0` to trace requests as they flow through the gateway.
  - **Metrics**: Exposes detailed performance metrics using `mpMetrics-5.0`.
  - **Health Checks**: Provides application health endpoints via `mpHealth-4.0`.
- **Configuration**: Uses `mpConfig-3.0` to manage application configuration, such as timeouts and endpoint URLs.
- **Validation**: Enforces data integrity using Jakarta Bean Validation 3.0.
- **Build & Formatting**: Managed by Gradle, with code quality enforced by Spotless.

## Prerequisites

- **JDK 21** or later.
- A running OpenTelemetry collector on `http://localhost:4317` to receive trace data (optional).

## Building the Project

The project is built using the Gradle wrapper.
