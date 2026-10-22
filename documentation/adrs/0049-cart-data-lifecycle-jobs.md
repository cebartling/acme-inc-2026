# ADR-0049: Cart Data Lifecycle Through Scheduled Jobs

## Status

Accepted. Since [ADR-0060](0060-cart-events-through-outbox.md) (PIN-384), each job writes its
event to the outbox in the same transaction as the cart's conditional write, instead of
publishing it best-effort afterwards (decision 3 and the `CartPurged` consequence), and the
scheduler has a fourth thread for the outbox relay (decision 7).

## Context

Three things happen to carts without a request to trigger them:

- An idle guest cart outlives the session cookie that was the only way to reach it
  (PIN-287).
- Carts that reached a final status pile up. Epic 009 asks for soft delete with retention
  (PIN-289).
- A checkout session lapses while its cart is still locked (PIN-330, PIN-383).

The shopping cart service runs as a single Spring Boot app with its own PostgreSQL database
([ADR-0041](0041-per-service-persistence-model.md)), so it needed a simple, in-process way
to do this work safely while customers keep using their carts.

## Decision

Each lifecycle step is a Spring `@Scheduled` job with its own `@ConditionalOnProperty`
switch, a use case, and a partial index for its scan.

| Job | Use case | Default schedule | Acts on | Switch |
| -- | -- | -- | -- | -- |
| `CartExpiryScheduledTasks` | `ExpireIdleGuestCartsUseCase` | `PT5M` delay, then every `PT1H` | `ACTIVE` guest carts idle past `acme.cart.guest-ttl` (`30d`) plus a day → `EXPIRED` | `acme.cart.expiry.enabled` |
| `CartPurgeScheduledTasks` | `PurgeFinalCartsUseCase` | `PT10M` delay, then every `PT6H` | `EXPIRED`, `MERGED`, `CONVERTED` carts with `updated_at` older than `acme.cart.retention` (`90d`) → deleted | `acme.cart.purge.enabled` |
| `CartCheckoutUnlockScheduledTasks` | `UnlockLapsedCheckoutsUseCase` | `PT1M` delay, then every `PT1M` | `CHECKOUT` carts whose session lapsed more than `acme.cart.checkout-unlock.grace` (`PT2M`) ago → `ACTIVE` | `acme.cart.checkout-unlock.enabled` |

1. **Scan a projection, then act per cart.** Each scan selects a small projection
   (`IdleGuestCart`, `FinalCart`, `LapsedCheckout`), oldest first, in batches of 500, using
   `ix_carts_idle_guests` (V3), `ix_carts_final` (V4, widened to `CONVERTED` in V9) or
   `ix_carts_checkout_expiry` (V7). No cart or line is loaded. `drainInBatches` stops at a
   short batch or a batch where nothing was done.
2. **A conditional write re-checks the condition.** `expireIfIdle`, `deleteIfFinalBefore`
   and `unlockIfLapsed` repeat the scan's condition in the UPDATE or DELETE, so a cart used,
   resumed or changed since the scan is left alone and two runs never act twice. Expiry and
   unlock bump the version ([ADR-0048](0048-cart-optimistic-locking.md)).
3. **Events only for carts actually changed.** `CartExpired`, `CartPurged` and
   `CheckoutSessionExpired` are published best-effort after each write, counted as
   `cart.expired`, `cart.purged` and `cart.checkout.expired`.
4. **Expiry never beats the cookie.** The idle limit is the cookie's `Max-Age` plus
   `ACTIVITY_REFRESH_INTERVAL` (one day), the same constant that throttles
   `touchGuestCart` ([ADR-0046](0046-guest-cart-identity-and-merge.md)). Expired rows are
   kept; user carts never expire.
5. **Purge deletes for real.** Lines go through the `cart_items` foreign key's `ON DELETE
   CASCADE`. `ACTIVE` and `CHECKOUT` carts are never deleted.
6. **Settings fail fast.** `guest-ttl` and `retention` must be positive and `grace` must not
   be negative, or startup fails.
7. **One thread per job.** `spring.task.scheduling.pool.size: 3`, so a slow purge run never
   holds up expiry or unlock.

## Consequences

### Positive

- Customers never wait on lifecycle work, and a job racing a customer can't undo the
  customer's change or revive a cart.
- Each job can be turned off, rescheduled or retuned through environment variables
  (`ACME_CART_*`).
- Repeated or overlapping runs are harmless.

### Negative

- Nothing coordinates runs across instances. Several instances would all scan; the
  conditional writes keep that correct but do redundant work.
- `CartPurged` is the only record of a purge. Events are best-effort and Debezium only
  captures `acme_orders`, so a lost publish leaves no trace of the deleted cart.
- Unlike the consent history of [ADR-0028](0028-append-only-consent-records.md), cart rows
  are not kept forever: after 90 days a final cart and its lines are gone.
- Some comments are behind the code: the `acme.cart.retention` comment in `application.yml`,
  `CartPurgeScheduledTasks`' KDoc and its log line still say only "expired and merged",
  though `CONVERTED` carts (PIN-331) are purged too.

### Mitigations

- The three counters show each job's throughput in Prometheus.
- A job that needs to run on one instance only can be given a lock or moved out of process
  later; that needs its own ADR.
