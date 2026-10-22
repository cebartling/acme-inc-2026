# ADR-0042: Best-Effort Event Publishing in Cart and Product

## Status

Accepted

Records an exception to [ADR-0022](0022-transactional-outbox-pattern.md), which the customer
and order services follow.

## Context

[ADR-0022](0022-transactional-outbox-pattern.md) moved customer events to a transactional
outbox, and the order service uses one too (`OutboxWriter`, `OutboxRelay`, PIN-368), "so an
order is never committed without its event" (`ARCHITECTURE.md`, Order Service).

The shopping cart and product services were built without an outbox:

- **Cart** use cases change the cart inside a `TransactionTemplate` and publish only after it
  returns, through `CartEventPublisher.publishLoggingFailure` in `CartSupport.kt`. That
  helper catches any exception from `CartEventPublisher.publish` and logs a warning with the
  event type, event ID and cart ID. Its comment reads: "Publishes after commit, best-effort
  (no outbox): a Kafka failure is logged and the customer's cart change still succeeds."
  Every cart event goes this way, from `ItemAddedToCart` to `CartConvertedToOrder`, including
  the ones the expiry, purge and checkout-unlock jobs publish.
- **Product** publishes `ProductViewed` (`GetProductDetailUseCase`) and `SearchExecuted` and
  `FiltersApplied` (`SearchProductsUseCase`) from read requests. There is no transaction
  around them. Each use case's `publishEvent` catches the failure and logs it.

Both publishers send to their topic (`cart.events`, `product.events`) keyed by aggregate ID,
then block on `kafkaTemplate.send(...).get(timeout)`. The timeout is
`acme.cart.events.publish-timeout-seconds` and `product.events.publish.timeout-seconds`,
both 10 seconds. No service in the repo consumes either topic yet. PIN-307 describes these
events as analytics.

## Decision

Cart and product events are **best-effort**:

1. **The state change, or the read, is what the request is for.** A Kafka failure or timeout
   never fails the request; the publisher's exception is caught and logged as a warning.
2. **Cart events are published after the transaction commits,** so a rolled-back or retried
   change (`retryOnConflict`, PIN-278) publishes nothing.
3. **There is no outbox, no retry and no replay.** An event that fails to send is gone.
4. **Events that other services must not miss go through an outbox** (ADR-0022), as the
   customer and order services do. A cart or product event that gains a consumer that
   depends on it needs that decision revisited.

## Consequences

### Positive

- Cart and product have no outbox table, relay or polling job to run and monitor.
- A Kafka outage does not stop customers from browsing, searching or changing their cart.
- An event is never published for a change that rolled back.

### Negative

- Events can be lost while the change is kept, so `cart.events` is not a complete history
  of carts. `CartConvertedToOrder` is lost the same way as any other cart event.
- The publish is in the request path. Each event waits up to 10 seconds for Kafka's
  acknowledgement, so a product search with filters can wait up to 20 seconds (PIN-307).
- Neither service sets the producer's `max.block.ms` in its `KafkaProducerConfig`, so
  `send()` itself can block for Kafka's 60-second default while it has no topic metadata,
  before the 10-second wait starts (PIN-369). The order service caps `MAX_BLOCK_MS_CONFIG`
  at its publish timeout; cart and product don't.
- A slow product search counts toward the customer app's search circuit breaker even
  though search itself is healthy (`ARCHITECTURE.md`, Product Service, Events).
- Services publish in different ways (identity's `UserEventPublisher` has no outbox either),
  so each new service has to choose.

### Mitigations

- Every failed publish is logged with its event type and ID.
- PIN-307 (send without waiting for the acknowledgement) and PIN-369 (cap `max.block.ms` in
  the cart) are open in the backlog to remove the request-path delay.
- Events carry an `eventId`, which ADR-0018 has consumers deduplicate by, so a topic moved
  to an outbox later, with at-least-once delivery, keeps the same envelope.
