# ACME Identity Service

Identity and authentication service for the ACME Inc. e-commerce platform.

## Overview

This service handles:
- User registration and authentication
- Password hashing with Argon2id
- Email verification token generation
- Event publishing to Kafka for downstream consumers

## Tech Stack

- **Language**: Kotlin 2.2
- **Runtime**: Java 24 with Project Loom (virtual threads)
- **Framework**: Spring Boot 4 with Spring MVC
- **Database**: PostgreSQL 16+
- **Messaging**: Apache Kafka with Avro/Schema Registry
- **Build Tool**: Gradle 9.2

## Prerequisites

- Java 24 (recommend using SDKMAN)
- Gradle 9.2
- PostgreSQL 16+
- Apache Kafka with Schema Registry

### Using SDKMAN

```bash
sdk env install
```

## Getting Started

### Running Locally

1. Start the required infrastructure (PostgreSQL, Kafka, Schema Registry):

```bash
# From the project root
docker compose up -d postgres kafka schema-registry
```

2. Run the application:

```bash
gradle bootRun
```

The service will be available at `http://localhost:10300`.

### API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/users/register` | Register a new user |
| GET | `/actuator/health` | Health check endpoint |
| GET | `/actuator/info` | Application info |
| GET | `/actuator/prometheus` | Prometheus metrics |

## Building

### Build the Application

```bash
gradle build
```

### Run Tests

```bash
gradle test
```

## Container Build

Build the container image using Podman or Docker:

```bash
# Using Podman
podman build -f Containerfile -t acme-identity-service .

# Using Docker
docker build -f Containerfile -t acme-identity-service .
```

### Running the Container

```bash
# Using Podman
podman run -d \
  --name identity-service \
  -p 10300:10300 \
  -e POSTGRES_HOST=postgres \
  -e POSTGRES_PORT=5432 \
  -e POSTGRES_DB=acme_identity \
  -e POSTGRES_USER=acme_app \
  -e POSTGRES_PASSWORD=acme_password \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:29092 \
  -e SCHEMA_REGISTRY_URL=http://schema-registry:8081 \
  --network acme-network \
  acme-identity-service

# Using Docker
docker run -d \
  --name identity-service \
  -p 10300:10300 \
  -e POSTGRES_HOST=postgres \
  -e POSTGRES_PORT=5432 \
  -e POSTGRES_DB=acme_identity \
  -e POSTGRES_USER=acme_app \
  -e POSTGRES_PASSWORD=acme_password \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:29092 \
  -e SCHEMA_REGISTRY_URL=http://schema-registry:8081 \
  --network acme-network \
  acme-identity-service
```

### Container Details

The Containerfile uses a multi-stage build:
- **Stage 1 (builder)**: Eclipse Temurin Java 24 JDK with Gradle 9.2
- **Stage 2 (runtime)**: Eclipse Temurin Java 24 JRE (slim)

Features:
- Non-root user (`acme:acme`) for security
- Health check via Spring Boot Actuator (`/actuator/health`)
- Container-optimized JVM settings:
  - `UseContainerSupport` for proper memory detection
  - `MaxRAMPercentage=75%` for heap sizing
  - `ExitOnOutOfMemoryError` for container restarts

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Application port | `10300` |
| `POSTGRES_HOST` | PostgreSQL hostname | `localhost` |
| `POSTGRES_PORT` | PostgreSQL port | `5432` |
| `POSTGRES_DB` | Database name | `acme_identity` |
| `POSTGRES_USER` | Database username | `acme_app` |
| `POSTGRES_PASSWORD` | Database password | `acme_password` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses | `localhost:9092` |
| `SCHEMA_REGISTRY_URL` | Schema Registry URL | `http://localhost:8081` |

## Project Structure

```
src/main/kotlin/com/acme/identity/
├── api/v1/                  # REST controllers and DTOs
├── application/             # Use cases / application services
├── domain/                  # Domain models and events
├── infrastructure/          # Repositories, messaging, security
└── config/                  # Spring configuration
```

## Related Documentation

- [US-0002-02: User Registration Processing](../../documentation/user-stories/0002-create-customer-profile/US-0002-02-user-registration-processing.md)
