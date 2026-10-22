# ADR-0055: Jackson 3 for HTTP, Jackson 2 for Kafka, Redis and JSONB

## Status

Accepted

## Context

Spring Boot 4 binds Spring MVC request and response bodies with Jackson 3, whose packages
are `tools.jackson.*`. The services' own JSON code was written against Jackson 2
(`com.fasterxml.jackson.*`): each has a `config/JacksonConfig.kt` that builds a Jackson 2
`ObjectMapper` with the Kotlin and `JavaTimeModule` modules and ISO-8601 dates.

PIN-250 (`a6799c0`) found the gap. Only the Jackson 2 Kotlin module was on the classpath, so
Jackson 3 ignored Kotlin constructor defaults: an omitted non-null field became null and
the request failed with a 400, e.g. `POST /api/v1/search` with no `page`. The fix added
`tools.jackson.module:jackson-module-kotlin` and kept the Jackson 2 modules, because each
service's `JacksonConfig` mapper "still serves Kafka, the event store and Redis, so
replacing them would break compilation".

## Decision

Every backend service runs both Jackson versions, each for its own job.

1. **Jackson 3 for HTTP.** Spring MVC's own mapper reads and writes request and response
   bodies. Each `build.gradle.kts` adds `tools.jackson.module:jackson-module-kotlin` so
   Kotlin defaults are honoured. `spring.jackson.*` settings configure this mapper only:
   product, cart and order set `deserialization.accept-float-as-int: false` (PIN-279,
   PIN-302), and order's `application.yml` notes it "only affects Spring MVC's Jackson 3
   mapper". Code that inspects a body-parsing failure catches
   `tools.jackson.core.JacksonException` (product's `GlobalExceptionHandler`, the cart's
   `CartController`, PIN-303).
2. **Jackson 2 for everything the service serializes itself.** The `JacksonConfig` bean
   turns Kafka event payloads into strings (every producer uses `StringSerializer`), and
   writes customer's and identity's `event_store` payloads, the outbox payloads in customer
   and order, and the cart's `product_snapshot` JSONB column (`AddItemToCartUseCase`,
   `StartCheckoutUseCase`). Customer's and identity's `RedisConfig` use Spring Data's
   `GenericJackson2JsonRedisSerializer`.

## Consequences

### Positive

- Kafka payloads, stored JSON and Redis values kept their existing serializer, so moving to
  Boot 4 changed nothing already written to a topic, a table or Redis.
- HTTP bodies get Boot 4's supported mapper and its `spring.jackson.*` configuration.

### Negative

- Two Jackson versions sit on every service's classpath, and an import picks one. Both
  define an `ObjectMapper`, so the wrong import compiles and serializes with the other
  version's settings.
- A setting made in one place doesn't reach the other. `accept-float-as-int: false`
  guards request bodies, not Kafka or JSONB; `JacksonConfig` settings don't reach HTTP.
- The `JacksonConfig` copies differ (see [ADR-0054](0054-independent-service-builds.md)).
  Customer, identity and notification mark the bean `@Primary`; product, cart and order
  don't. Only notification turns off `FAIL_ON_UNKNOWN_PROPERTIES`. The Redis serializers
  build their own mapper and use neither bean.
- Build comments were copied without editing: product's says the Jackson 2 modules serve
  "Kafka, event store, Redis", though product has no event store or Redis.

### Mitigations

- Each `build.gradle.kts` comments which Jackson serves what, beside the two dependencies.
- Moving Kafka, JSONB and Redis to Jackson 3 is one change per service, and needs a check
  that the new mapper reads what the old one wrote.
