# ADR-0062: Abandoned Orders Are Cancelled, and a Crash After Convert Is Alerted On

## Status

Accepted

## Context

Placing an order saves it `PENDING`, converts the cart, then marks it `SUBMITTED`. If the cart
doesn't answer, the customer gets `503 CART_UNAVAILABLE`, and the order stays `PENDING` until
they retry with the same `Idempotency-Key`. Nothing cleaned up an order whose retry never came
(PIN-427).

A scheduled job can't finish the placement. Converting forwards the customer's access token
(ADR-0044, ADR-0061), the cart finds the cart's owner from that token, and a job has no token.

The service can also crash between a successful convert and `markSubmitted`. That leaves a
`PENDING` order whose cart is already `CONVERTED`. The job can't see this, because reading the
cart needs a token too.

## Decision

1. **The job only cancels.** `CancelAbandonedOrdersUseCase` moves `PENDING` orders older than
   `acme.order.abandoned.max-age` (default one hour) to `CANCELLED` with
   `PLACEMENT_ABANDONED`, one bounded batch per run. It uses the conditional
   `OrderRepository.markCancelled`, so a racing retry leaves the order either `SUBMITTED` or
   `CANCELLED`. These orders stay hidden from the customer, like other failed placements.
2. **A late retry is refused for good.** The same key answers 404 `PLACEMENT_ABANDONED`
   through `PlaceOrderUseCase.resume`, and the customer app treats that as an expired
   checkout.
3. **A crash after convert is accepted and alerted on.** Every cancel logs a warning with
   the order, user and cart, and counts in `orders_cancelled_total`, tagged with `reason`, so
   support can follow up. We don't add a service-token cart read asking whether the cart was
   converted for this order. It's a new endpoint for a rare crash, and this is the smaller
   change.
4. **The minimum age is a constant.** The service refuses to start unless `max-age` is over
   33 minutes: the cart's 30-minute checkout session, 2-minute unlock grace and 1-minute unlock
   interval. The order service can't read the cart's settings, so these are the cart's
   defaults.

## Consequences

- A customer in the crash case has no cart and no order until support acts on the warning.
  The same happens if the job wins a race with a retry whose convert succeeded, which needs
  a retry at the very moment the order passes `max-age`.
- The job can cancel an order whose checkout session is still live. Resuming a session moves
  its expiry out, so reloading `/checkout` can keep it alive past `max-age`. Nothing is lost:
  the cart is still locked and unconverted, and the customer starts checkout again.
- Raising the cart's session length, grace or interval past the constant needs the constant
  or `max-age` raised too. Nothing checks this across the two services.
- Every instance runs the job (PIN-390). The conditional update keeps that harmless, and
  only the instance that cancelled an order logs and counts it.
- The order service has no Prometheus registry yet, so the metric shows at
  `/actuator/metrics` only and nothing alerts on it automatically.
