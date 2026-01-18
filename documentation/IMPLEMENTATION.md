# Implementation Guidelines


## Core infrastructure

- Use Docker Compose to manage infrastructure services
    - YAML file in repository root directory
    - Dependency for all backend services and frontend applications 
    - Initialization scripts should be managed under the `docker` directory 
- PostgreSQL 16+ for read-write, command store
    - Use a Debezium-sourced image that has logical replication enabled and replication slots configured 
    - `quay.io/debezium/postgres:latest`
- MongoDB 8.2+ for read-only, query store
- Hashicorp Vault for secrets management
- Confluent Kafka for events and messaging
- Confluent Schema Registry for Avro schemas for Kafka messaging
- Debezium Kafka Connect connector for change data capture on PostgreSQL tables
    - `quay.io/debezium/connect:latest`
- Grafana for observability dashboards
- Tempo for OTel tracing backend
- Loki for OTel logging via OTel Collector
- Prometheus for OTel metrics via OTel SDK/Collector

### Distroless container images (Tempo and Loki)

Grafana Tempo (since v2.8.0) and Loki use Google's distroless base container images. These are minimal images that contain only the application and its runtime dependencies—no shells, package managers, or unnecessary system utilities.

**Security benefits:**

- **Reduced attack surface**: Without shells (`/bin/sh`, `/bin/bash`) or package managers, attackers who breach the container have fewer tools to exploit for lateral movement or privilege escalation
- **Fewer CVEs**: Minimal components mean fewer vulnerabilities to track and patch
- **Non-root execution**: Both run as UID `10001` (user `tempo`/`loki`) rather than root, following the principle of least privilege
- **Read-only filesystem**: Containers enforce read-only root filesystems and drop all Linux capabilities

**Operational considerations:**

- **No shell access for debugging**: Use `kubectl debug` or ephemeral containers for troubleshooting
- **Multi-stage builds**: Application artifacts are built in full-featured containers and copied to distroless runtime images
- **File permissions**: When upgrading from older versions, existing data files may need ownership changes to UID `10001`

**Image sizes:**

- Distroless static images are ~2 MiB (vs ~124 MiB for Debian, ~5 MiB for Alpine)
- Smaller images mean faster pull times and reduced storage costs


## Backend services

- Kotlin 2.2
- Java 24
    - Project Loom: virtual threads usage
- Spring Boot 4
    - Spring MVC
    - Blocking I/O
    - JPA
    - Actuator metrics
- Gradle 9.2 build infrastructure
- SDKMAN Java runtime management
    - Set both java and gradle in the .sdkmanrc file   
- PostgreSQL for read-write, command store
- MongoDB for read-only, query store
- Caffeine for in-memory caching
- OpenTelemtery for distributed tracing, metrics and structured logging
- Spring Actuator health check endpoints
- REST endpoints
- Kafka for messaging
- Arrow Kotlin for typed error handling
- JUnit 6 and Mockito for unit testing
- Use KDoc comments throughout the code


### CQRS implementation

- Use saga pattern for reversible business transactions when implementing commands
- The workflow orchestrator service will house the saga pattern-based workflows
- Debezium Kafka Connect connector to implement change data capture between command store and query store

### Caffeine in-memory caching

Caffeine is a high-performance, near-optimal caching library for Java/Kotlin applications.

**Integration with Spring Boot:**

- Use `spring-boot-starter-cache` with Caffeine as the cache provider
- Configure via `application.yml` or programmatically with `CaffeineCacheManager`
- Enable caching with `@EnableCaching` on a configuration class

**Cache configuration options:**

- `maximumSize`: Maximum number of entries in the cache
- `expireAfterWrite`: Time-based expiration after entry creation
- `expireAfterAccess`: Time-based expiration after last access
- `refreshAfterWrite`: Asynchronous refresh of entries after write
- `recordStats`: Enable cache statistics for monitoring

**Usage patterns:**

- `@Cacheable`: Cache method results based on parameters
- `@CachePut`: Update cache without interfering with method execution
- `@CacheEvict`: Remove entries from cache (single or all)
- Use cache names to organize caches by domain (e.g., `users`, `products`, `sessions`)

**Best practices:**

- Define cache specifications per cache name for fine-grained control
- Use async cache loading for expensive computations
- Integrate cache metrics with Micrometer for observability
- Consider write-behind patterns for cache-aside with database writes
- Size caches based on memory constraints and access patterns

### Arrow Kotlin for typed error handling

Arrow is a functional programming library for Kotlin that provides type-safe error handling and other functional patterns.

**Dependencies (all backend services):**

```kotlin
dependencies {
    // Arrow Core - Either, Option, Raise DSL
    implementation("io.arrow-kt:arrow-core:2.1.0")

    // Arrow Fx Coroutines - resilience, parallel ops
    implementation("io.arrow-kt:arrow-fx-coroutines:2.1.0")

    // Arrow Core Serialization - Jackson integration
    implementation("io.arrow-kt:arrow-core-serialization:2.1.0")
}
```

**Error handling pattern:**

Use `Either<Error, Success>` instead of custom sealed Result classes:

```kotlin
// Define error hierarchy as sealed interface
sealed interface RegistrationError {
    data class DuplicateEmail(val email: String) : RegistrationError
    data class ValidationError(val message: String) : RegistrationError
}

// Use case returns Either
fun execute(request: RegisterUserRequest): Either<RegistrationError, RegisterUserResponse> {
    return either {
        // ensure() raises error if condition is false
        ensure(!userRepository.existsByEmail(request.email)) {
            RegistrationError.DuplicateEmail(request.email)
        }

        // ensureNotNull() raises error if value is null
        val user = ensureNotNull(userRepository.findById(id)) {
            RegistrationError.ValidationError("User not found")
        }

        // Return success value
        RegisterUserResponse(userId = user.id)
    }
}
```

**Controller integration:**

Use `.fold()` to handle Either results in controllers:

```kotlin
@PostMapping("/register")
fun register(@RequestBody request: RegisterUserRequest): ResponseEntity<*> {
    return registerUserUseCase.execute(request).fold(
        ifLeft = { error ->
            when (error) {
                is RegistrationError.DuplicateEmail ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(error.email))
                is RegistrationError.ValidationError ->
                    ResponseEntity.badRequest().body(ErrorResponse(error.message))
            }
        },
        ifRight = { response ->
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        }
    )
}
```

**Event handler integration:**

```kotlin
activateCustomerUseCase.execute(userId, activatedAt).fold(
    ifLeft = { error ->
        when (error) {
            is ActivateCustomerError.AlreadyActive -> logger.info("Already active")
            is ActivateCustomerError.CustomerNotFound -> throw RuntimeException("Not found")
            is ActivateCustomerError.Failure -> throw RuntimeException(error.message)
        }
    },
    ifRight = { success ->
        logger.info("Activated customer: ${success.customer.id}")
    }
)
```

**Testing pattern:**

```kotlin
@Test
fun `should return success`() {
    val result = useCase.execute(request)

    assertTrue(result.isRight())
    val success = result.getOrElse { fail("Expected success") }
    assertEquals(expectedId, success.userId)
}

@Test
fun `should return error for duplicate`() {
    val result = useCase.execute(duplicateRequest)

    assertTrue(result.isLeft())
    result.fold(
        ifLeft = { error ->
            assertIs<RegistrationError.DuplicateEmail>(error)
            assertEquals(email, error.email)
        },
        ifRight = { fail("Expected error") }
    )
}
```

**Best practices:**

- Define error types as sealed interfaces for exhaustive when expressions
- Use `ensure()` and `ensureNotNull()` inside `either { }` blocks for validation
- Return `Error.left()` and `Success.right()` when not using the DSL
- Use `.fold()` in controllers for type-safe response mapping
- Prefer `Either` over exceptions for expected business errors

### Spring Boot 4 Breaking Changes

When creating new backend services, be aware of these Spring Boot 4 changes:

**MongoDB Configuration:**
- Property prefix changed from `spring.data.mongodb.*` to `spring.mongodb.*`
- Example:
  ```yaml
  spring:
    mongodb:
      host: ${MONGODB_HOST:localhost}
      port: ${MONGODB_PORT:27017}
      database: ${MONGODB_DB:acme_query}
  ```

**Flyway Migrations:**
- Flyway auto-configuration may not trigger automatically in Spring Boot 4
- Workaround: Use `hibernate.ddl-auto: update` for development, or apply migrations manually
- Ensure Flyway dependencies include both `flyway-core` and `flyway-database-postgresql`

**Rate Limiting for Testing:**
- The Identity Service implements rate limiting (5 requests/minute per IP by default)
- Disable for acceptance tests: `RATE_LIMITING_ENABLED=false`
- Configure in `identity.rate-limiting.enabled` property

### Service Ports

Backend services use ports starting at 10300 to avoid conflicts:

| Service | Port |
|---------|------|
| Identity Service | 10300 |
| Customer Service | 10301 |
| (future services) | 10302+ |

### Service projects

- Identity management: `/backend-services/identity`
- Product catalog: `/backend-services/product`
- Inventory management: `/backend-services/inventory`
- Customer management: `/backend-services/customer`
- Order management: `/backend-services/order`
- Notifications management: `/backend-services/notification`
- Shipping and fulfillment management: `/backend-services/fulfillment`
- Payment management: `/backend-services/payment`
- Pricing management: `/backend-services/pricing`
- Shopping cart management: `/backend-services/shopping-cart`
- Analytics: `/backend-services/analytics`


## Frontend applications

- Tanstack Start
- React 19.2
- Vite
- Set Node.js runtime with nvm
- Node.js 24 (`lts/krypton`)
- Tailwind CSS 4 for styling
- shadcn/ui UI components
- shadcn icons
- Zod for schemas, validation
- Zustand for state management
- React Hook Form for user input forms
- TanStack Query for data fetching
- TanStack Table and Virtual for data tables
- TanStack Router for routing
- Mock Service Worker (msw) for API endpoint mocking
- Vitest for unit testing
- Use JSDoc comments throughout the code

### Application projects

- Customer-facing: `/frontend-apps/customer`
- Admin-facing: `/frontend-apps/admin`

## Acceptance testing

- User story acceptance criteria will be used to craft automated acceptance tests
- Cucumber.js should be used for automating acceptance tests
- Playwright should be used for browser automation
- Acceptance tests project will reside in the `acceptance-tests` directory.
- Use Allure Report 3 for HTML Cucumber test run reporting
    - https://allurereport.org/docs/v3/

### API Response Mapping

When writing step definitions, ensure property paths match the actual API response structure:

| API Response | Step Definition Access |
|--------------|----------------------|
| `customerId` | `customerProfile.customerId` (not `id`) |
| `email.address` | `customerProfile.email?.address` |
| `name.displayName` | `customerProfile.name?.displayName` |
| `profile.preferredLocale` | `customerProfile.profile?.preferredLocale` |
| `preferences.communication.marketing` | `customerProfile.preferences?.communication?.marketing` |

### Async Event Testing

For tests that depend on Kafka event processing:

- Use a `waitFor` helper function with appropriate timeout (10+ seconds)
- Poll the API endpoint until the expected data appears
- Example pattern:
  ```typescript
  const found = await waitFor(async () => {
    const response = await apiClient.get(`/api/v1/customers/by-email/${email}`);
    return response.status === 200;
  }, 10000);
  expect(found).toBe(true);
  ```

### Running Acceptance Tests

```bash
# Start infrastructure
docker compose up -d

# Start services with rate limiting disabled
RATE_LIMITING_ENABLED=false docker compose -f docker-compose.apps.yml up -d

# Run API tests
cd acceptance-tests
CUSTOMER_API_URL=http://localhost:10301 IDENTITY_API_URL=http://localhost:10300 npm test -- --tags @api
``` 

