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

```bash
# On Linux or macOS
./gradlew build

# On Windows
.\gradlew.bat build
```

This command will compile the code, run tests, and create a deployable web application archive (WAR) at `build/libs/gateway.war`.

## Running the Application

The easiest way to run the application is using the Open Liberty `libertyDev` task, which provides live-reloading of code changes.

```bash
# On Linux or macOS
./gradlew libertyDev

# On Windows
.\gradlew.bat libertyDev
```

Once the server starts, the application will be available at:

- **Application Context**: `http://localhost:9080/gateway`
- **SOAP WSDL**: `http://localhost:9080/gateway/GatewayService?wsdl`

You can use a tool like SoapUI or Postman to send a SOAP request to the WSDL endpoint.

## Running Tests

The project includes both unit tests and integration tests.

### Running All Tests

To run all tests (unit and integration), use the `test` task.

```bash
./gradlew test
```

A test report will be generated at `build/reports/tests/test/index.html`.

### Running a Specific Test

You can run a single test class or method using the `--tests` filter.

```bash
# Run all tests in a specific class
./gradlew test --tests "com.example.GatewayServiceImplTest"

# Run a specific method
./gradlew test --tests "com.example.GatewayServiceImplTest.processRequest_shouldThrowWebServiceException_whenValidationFails"
```

### Integration Tests

The integration tests (e.g., `RestServiceClientIT.java`) use **WireMock** to simulate the downstream REST service and test the fault tolerance policies. To run them, you must have the Open Liberty server running in a separate terminal.

1.  **Terminal 1: Start the server.**
    ```bash
    ./gradlew libertyDev
    ```

2.  **Terminal 2: Run the integration test.**
    ```bash
    ./gradlew test --tests "com.example.RestServiceClientIT"
    ```

## Project Structure

```
├── build.gradle                  # The main build script for the project.
├── gradle
│   └── wrapper                   # Gradle Wrapper files.
├── gradlew                       # Gradle Wrapper script for *nix.
├── gradlew.bat                   # Gradle Wrapper script for Windows.
└── src
    ├── main
    │   ├── java                  # Application source code.
    │   ├── liberty/config
    │   │   └── server.xml        # Open Liberty server configuration.
    │   └── webapp/WEB-INF
    │       └── beans.xml         # CDI bean descriptor.
    └── test
        └── java                  # Unit and integration tests.
```

## Configuration

- **`server.xml`**: The primary server configuration file. It defines which MicroProfile and Jakarta EE features are enabled, configures logging, telemetry, and deploys the application.
- **`build.gradle`**: The `ext` block in this file sets server variables like HTTP ports, which are injected into `server.xml`.
- **MicroProfile Config**: Application-level properties (e.g., timeouts) are managed via MicroProfile Config. These can be provided through `server.xml`, system properties, or environment variables.

## Observability Endpoints

Once the server is running, you can access the following endpoints for monitoring and diagnostics:

-   **WSDL**: `http://localhost:9080/gateway/GatewayService?wsdl`
  -   The service definition for SOAP clients.
-   **Health (Liveness)**: `http://localhost:9080/health/live`
  -   Checks if the server process is running.
-   **Health (Readiness)**: `http://localhost:9080/health/ready`
  -   Checks if the application is ready to handle requests.
-   **Metrics (Prometheus Format)**: `http://localhost:9080/metrics`
  -   Exposes performance metrics for monitoring systems like Prometheus.
-   **Metrics (JSON Format)**: `http://localhost:9080/metrics?format=json`
  -   Provides the same metrics in a human-readable JSON format.v