# ADR-0060: Cart Events Through the Transactional Outbox

## Status

Accepted

Supersedes [ADR-0042](0042-best-effort-event-publishing.md) for the shopping cart service.
The product service stays best-effort under ADR-0042.

## Context

Under [ADR-0042](0042-best-effort-event-publishing.md), cart use cases published each event
to `cart.events` after their transaction committed, through `publishLoggingFailure`, which
logged a failed send and dropped the event.

PIN-384 found the case that matters most. `ConvertCartUseCase` converts the cart into the
order the order service placed (PIN-331), then publishes `CartConvertedToOrder`. If Kafka
times out after the convert commits, the failure is logged and the convert still answers 200.
A retry by the order service finds the cart already `CONVERTED`, and that repeat publishes
nothing, by design. So no `CartConvertedToOrder` is ever published for that order.

The same is true of every cart event. Each publish also blocked the request for up to
10 seconds waiting for Kafka's acknowledgement, and up to 60 seconds while the producer had
no topic metadata (PIN-369). No service consumes `cart.events` yet.

The order service already has a transactional outbox (PIN-368, ADR-0022).

## Decision

**Every cart event goes through a transactional outbox**, built the same way as the order
service's:

1. **Writing.** Use cases and jobs call `OutboxWriter.write(event)` inside the transaction that
   makes the change. The writer uses `Propagation.MANDATORY`, so called outside a
   transaction it throws. The event commits or rolls back with the change, including a
   `retryOnConflict` attempt that loses the race.
2. **Jobs** (expire, purge, checkout unlock) wrap each cart's conditional UPDATE or DELETE and
   its event in one `TransactionTemplate`, so an event is written only for a cart that run
   actually changed.
3. **Sending.** `OutboxRelay` polls the V10 `outbox` table and sends unsent messages to
   `cart.events` in write order, keyed by cart ID. A failed send is retried on later polls
   and never given up. Delivery is at least once, so consumers deduplicate by `eventId`
   (ADR-0018).
4. **Kept the same.** The event envelope, topic and key don't change, so a future consumer
   sees the same messages.
5. **The code is copied, not shared**, following [ADR-0054](0054-independent-service-builds.md):
   the cart has its own `OutboxMessage`, `OutboxRepository`, `OutboxWriter` and `OutboxRelay`.

The producer's `max.block.ms` is capped at `acme.cart.events.publish-timeout-seconds`, as in
the order service, so the relay's timeout holds while Kafka is down.

## Consequences

### Positive

- A committed cart change always leads to its event being published, including
  `CartConvertedToOrder` (PIN-384).
- Kafka is off the request path, so an outage neither fails nor slows a cart request or job.
- The cart and order services publish events the same way.

### Negative

- The cart runs one more table and polling job. A message that keeps failing holds up every
  cart event behind it, and is logged as an error from `alert-after-attempts` on.
- An event can be sent twice.
- Sent rows are not purged yet, and only one instance may run the relay (PIN-390), as in the
  order service.
- `CheckoutSessionExpiry` counts an expiry when its event is written, so a write that a lost
  race rolls back and retries is counted twice.
