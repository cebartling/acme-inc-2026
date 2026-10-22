# ADR-0054: Independent Per-Service Builds with No Shared Code

## Status

Accepted

Builds on [ADR-0001](0001-microservices-architecture.md) and
[ADR-0010](0010-kotlin-spring-boot-backend.md).

## Context

The six backend services live in one repository under `backend-services/`: customer,
identity, notification, order, product and shopping-cart. A monorepo invites a shared
build: a root `settings.gradle.kts` that includes every service, and common modules for
code that each service would otherwise repeat, such as the `DomainEvent` base class, the
`JacksonConfig` bean or the outbox.

The repository has neither. There is no Gradle build at the repository root, and no
service's `build.gradle.kts` or `settings.gradle.kts` uses `includeBuild`, `project(...)`
or `mavenLocal()`. Each service directory is a complete Gradle project on its own.

## Decision

Each backend service is built, tested and packaged on its own, and shares no code with the
others.

1. **One Gradle project per service.** Each directory has its own `settings.gradle.kts`
   (with its own `rootProject.name`), `build.gradle.kts`, `gradlew` wrapper and `.sdkmanrc`
   (`java=25.0.3-tem`, `gradle=9.5.0`). Plugin and library versions are declared in each
   `build.gradle.kts`; today all six use Spring Boot 4.1.1 and Kotlin 2.4.20.
2. **One container per service.** Each has its own `Containerfile`, and
   `docker-compose.apps.yml` builds each service from its own directory as the context.
3. **CI runs a per-service matrix.** The `backend-tests` job in `.github/workflows/ci.yml`
   runs `./gradlew test` in `backend-services/<service>` for each of the six services, with
   `fail-fast: false` and a test-results upload per service. `scripts/run-unit-tests.sh`
   runs the same suites locally, one flag per service (PIN-253 notes that the two must be
   kept in step by hand).
4. **No shared modules.** A pattern one service needs from another is copied into it and
   adapted. A new service is scaffolded from an existing one: the cart was "modeled on the
   product service" with "its own Gradle wrapper" (PIN-266), and the order service skeleton
   was added the same way (PIN-364).

## Consequences

### Positive

- A service builds and tests with nothing but its own directory, so a change to one service
  can't break another's compile, and a CI failure names the one service at fault.
- Each service carries only the libraries it uses: Twilio is only in identity's build,
  SendGrid only in notification's and MongoDB only in customer's.
- A copy can be changed to fit its service. Order's outbox (PIN-368) differs from
  customer's in four ways, listed in [ADR-0022](0022-transactional-outbox-pattern.md).

### Negative

- Common code is repeated. Every service has its own `domain/events/DomainEvent.kt` and
  `config/JacksonConfig.kt`, and order has its own copy of customer's outbox
  (ADR-0022: "since the services share no code").
- The copies drift. Identity's and notification's `DomainEvent` have no `causationId`,
  and notification's declares its properties `open`. Customer, identity and notification
  mark their `ObjectMapper` `@Primary`; product, cart and order don't. Notification's
  `rootProject.name` is `notification` where the others end in `-service`.
- A cross-cutting fix lands once per service. Adding the Jackson 3 Kotlin module (PIN-250,
  `a6799c0`) meant editing four `build.gradle.kts` files, and keeping versions aligned is a
  manual job across six.

### Mitigations

- When a pattern is copied, the copy's differences are written down where the pattern is
  recorded, as ADR-0022 does for order's outbox.
- Adding a service means adding it to the CI matrix, `run-unit-tests.sh` and Compose in the
  same change, as PIN-266 and PIN-364 did.
