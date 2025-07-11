# SOAP to REST Gateway

This project is a Java web application designed to act as a gateway, built with Gradle and intended for deployment on an Open Liberty application server.

## Prerequisites

*   Java Development Kit (JDK) 21 or later.
*   Gradle is used for building the project (the Gradle wrapper is included).

## Building the Project

To build the project and create the WAR file, run the following command from the project's root directory:

```bash
./gradlew build
```

This command will compile the code, run tests, and package the application into a WAR file located at `build/libs/gateway.war`.

## Running the Application

The project is configured with the Open Liberty Gradle plugin, which makes it easy to run the application on a development server.

1.  **Start the server in development mode:**

    ```bash
    ./gradlew libertyDev
    ```

    Development mode (`libertyDev`) allows for hot-reloading of code changes without restarting the server.

2.  **Start the server in run mode:**

    ```bash
    ./gradlew libertyRun
    ```

Once the server is running, the application will be accessible at:
`http://localhost:9080/gateway`

To stop the server, you can press `CTRL+C` in the terminal where it's running, or execute:

```bash
./gradlew libertyStop
```

## Code Style and Formatting

This project uses Spotless with the Google Java Format to maintain a consistent code style.

To apply formatting to the source code, run:

```bash
./gradlew spotlessApply
```

The build is configured to automatically apply formatting before compiling the Java source code.

## Key Technologies

*   **Runtime**: Open Liberty
*   **APIs**: Jakarta EE 10, MicroProfile 6.0
*   **Build Tool**: Gradle
*   **Language**: Java 21