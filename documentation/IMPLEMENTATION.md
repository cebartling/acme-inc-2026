# Implementation Guidelines

## Technologies

### Backend services

- Backend services will be implemented in Kotlin and Spring Boot
- Kotlin 2.2
- Java 24
    - Project Loom: virtual threads usage
- Spring Boot 4
    - Spring MVC
    - Blocking I/O
    - JPA
    - Actuator metrics
- Gradle build infrastructure
- PostgreSQL for read-write, command store
- MongoDB for read-only, query store
- OpenTelemtery for distributed tracing, metrics and structured logging
- Spring Actuator health check endpoints
- REST endpoints
- Kafka for messaging
- JUnit 6 and Mockito for unit testing

#### CQRS implementation

- Use saga pattern for reversible business transactions when implementing commands
- The workflow orchestrator service will house the saga pattern-based workflows
- Debezium Kafka Connect connector to implement change data capture in command store


### Frontend applications

- React 19.2
- Vite
- Tailwind CSS 4 for styling
- shadcn/ui UI components
- Zod for schemas, validation
- Zustand for state management
- TanStack Query for data fetching
- TanStack Table and Virtual for data tables
- TanStack Router for routing
- Vitest for unit testing

## Acceptance testing

- User story acceptance criteria will be used to craft automated acceptance tests
- Cucumber.js will be used for automating acceptance tests
- Playwright will be used for browser automation
-