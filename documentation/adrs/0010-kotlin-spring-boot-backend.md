# ADR-0010: Kotlin with Spring Boot for Backend Services

## Status

Accepted

**Current state (2026-10-21, PIN-386):** All six services (identity, customer, notification, product, shopping-cart, order) are built with Spring Boot 4.1.1, Kotlin 2.4.20 and a Java 25 toolchain, and the versions below have been corrected to match. Several stack items were never adopted. Every service uses Spring MVC (`spring-boot-starter-web`), not WebFlux, plus Spring Data JPA, and only customer adds MongoDB (blocking, not reactive). No service depends on Spring Cloud or on `spring-cloud-starter-vault-config` (see ADR-0009), so treat the dependency block below as the original plan.

## Context

Backend services need a technology stack that:

- Enables rapid development with strong type safety
- Provides excellent support for microservices patterns (health checks, metrics, tracing)
- Has mature libraries for database access, messaging, and web APIs
- Offers good developer experience with modern language features
- Has strong hiring market and community support

Candidates considered:
- **Kotlin + Spring Boot**: Modern JVM language with mature framework
- **Java + Spring Boot**: Established but more verbose
- **Go**: Excellent performance but less mature web ecosystem
- **Node.js + TypeScript**: Good for I/O-heavy workloads but weaker type safety at runtime
- **Rust**: Performance-focused but steep learning curve

## Decision

We will use **Kotlin** with **Spring Boot 4** for all backend microservices.

Stack components:
- **Kotlin 2.4**: Concise, null-safe, coroutine support
- **Spring Boot 4.1**: Auto-configuration, actuator, dependency management
- **Spring WebFlux** or **Spring MVC**: Reactive or traditional web layer
- **Spring Data**: Repository abstractions for PostgreSQL (JPA) and MongoDB
- **Spring Cloud**: Distributed tracing, config client
- **Gradle (Kotlin DSL)**: Build system with Kotlin scripts

Project structure:
```
service/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/acme/service/
│   │   │       ├── Application.kt
│   │   │       ├── api/          # Controllers, DTOs
│   │   │       ├── domain/       # Aggregates, Events, Commands
│   │   │       ├── application/  # Command/Query handlers
│   │   │       ├── infrastructure/  # DB, Messaging adapters
│   │   │       └── config/       # Spring configuration
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── kotlin/
├── build.gradle.kts
└── Containerfile
```

Key dependencies:
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    implementation("org.springframework.cloud:spring-cloud-starter-vault-config")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("org.springframework.kafka:spring-kafka")
}
```

## Consequences

### Positive

- **Null Safety**: Kotlin's type system prevents null pointer exceptions at compile time
- **Conciseness**: Less boilerplate than Java, data classes, extension functions
- **Coroutines**: Native support for asynchronous, non-blocking code
- **Spring Integration**: First-class Kotlin support in Spring Boot 4
- **Java Interop**: Full access to Java ecosystem and libraries
- **Tooling**: Excellent IDE support (IntelliJ IDEA)

### Negative

- **JVM Overhead**: Higher memory footprint than Go or Rust
- **Startup Time**: JVM cold start slower than native binaries (mitigated by GraalVM)
- **Build Times**: Gradle builds can be slow for large projects
- **Kotlin Adoption**: Smaller talent pool than Java (though growing rapidly)

### Mitigations

- Use GraalVM native image compilation for latency-sensitive services
- Configure JVM ergonomics for container environments
- Use Gradle build cache and incremental compilation
- Invest in Kotlin training for Java developers (minimal ramp-up time)
- Share common libraries across services to reduce per-service code
