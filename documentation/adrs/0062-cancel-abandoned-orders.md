# ADR-0062: Abandoned Orders Are Cancelled, and a Lost Convert Is Logged for Support Until the Cart Read Exists

## Status

Accepted

Amended in PIN-464: decision 3 now commits to a service-token cart read (PIN-491) instead
of declining it. PIN-491 must also amend [ADR-0061](0061-order-service-token-for-cart-calls.md),
because the read has no customer token (see decision 3).

## Context

Placing an order saves it `PENDING`, converts the cart, then marks it `SUBMITTED`. If the cart
doesn't answer, the customer gets `503 CART_UNAVAILABLE`, and the order stays `PENDING` until
they retry with the same `Idempotency-Key`. Nothing cleaned up an order whose retry never came
(PIN-427).

A scheduled job can't finish the placement. Converting forwards the customer's access token
(ADR-0044, ADR-0061), the cart finds the cart's owner from that token, and a job has no token.

A convert can also succeed without the order hearing of it. The service can crash before
`markSubmitted`, or the cart can commit the convert but answer after the 2-second read timeout,
so the customer gets `503 CART_UNAVAILABLE` and may never retry. The timeout case needs no
crash, so it's more than an edge case (PIN-464). Either way the order is
`PENDING` and its cart is already `CONVERTED`. The job can't see this, because reading the cart
needs a token too, until the service-token read of decision 3 exists.

## Decision

1. **The job only cancels, until PIN-491.** `CancelAbandonedOrdersUseCase` moves `PENDING`
   orders older than `acme.order.abandoned.max-age` (default one hour) to `CANCELLED` with
   `PLACEMENT_ABANDONED`, one bounded batch per run. It uses the conditional
   `OrderRepository.markCancelled`, so a racing retry leaves the order either `SUBMITTED` or
   `CANCELLED`. These orders stay hidden from the customer, like other failed placements
   (recorded as `cancelled_by = PLACEMENT` since [ADR-0063](0063-customer-order-cancellation.md)).
2. **A late retry is refused for good.** The same key answers 404 `PLACEMENT_ABANDONED`
   through `PlaceOrderUseCase.resume`, and the customer app treats that as an expired
   checkout.
3. **A lost convert is logged for support until the cart read exists.** Every cancel logs a
   warning with the order, user and cart, and counts in `orders_cancelled_total`, tagged with
   `reason`, so support can follow up. We will add a service-token cart read asking whether
   the cart was converted for this order, so the job can finish the placement instead of
   cancelling (PIN-491). PIN-464 reversed the original choice to skip it: a customer who loses
   both cart and order is worse than the cost of a new endpoint. The job has no customer token, so
   the read can't use the owner check ADR-0061 keeps on the cart's two order-service calls.
   PIN-491 decides how the cart authorizes it and amends ADR-0061. Until PIN-491 ships, this
   warning is the only trace in the order service. The cart keeps the order's ID on the
   `CONVERTED` cart (`carts.order_id`), which is how support can confirm a lost convert.
4. **The minimum age is a constant.** The service refuses to start unless `max-age` is over
   33 minutes: the cart's 30-minute checkout session, 2-minute unlock grace and 1-minute unlock
   interval. The order service can't read the cart's settings, so these are the cart's
   defaults.

## Consequences

- A customer whose convert was lost has no cart and no order until support acts on the
  warning.
- The job can cancel an order whose checkout session is still live. Resuming a session moves
  its expiry out, so reloading `/checkout` can keep it alive past `max-age`. Nothing is lost,
  but the cart can stay locked until the session lapses (see the cart-lock decision below),
  and the customer starts checkout again.
- Raising the cart's session length, grace or interval past the constant needs the constant
  or `max-age` raised too. Nothing checks this across the two services.
- Every instance runs the job (PIN-390). The conditional update keeps that harmless, and
  only the instance that cancelled an order logs and counts it.
- The order service has no Prometheus registry yet, so the metric shows at
  `/actuator/metrics` only and nothing alerts on it automatically.
- **Decided in PIN-464: a late retry can still race the job, and we leave it.** The job
  measures age from `createdAt` and a retry doesn't update it, so the job can cancel an order
  between a retry's successful convert and `markSubmitted`. The cart ends `CONVERTED`, the
  order `CANCELLED`, and the customer gets 404 `PLACEMENT_ABANDONED`. It needs a retry at the
  moment the order passes `max-age`. That case is known for certain, so `PlaceOrderUseCase`
  logs it as an error naming the cart, order and user, and support acts on it like a lost
  convert. Closing it means a retry updating `updatedAt` and the job filtering on it instead
  of on `createdAt`, which changes decision 1's age rule. PIN-491's cart check narrows the
  window but doesn't close it: a retry can still convert between the check and the cancel.
- **Decided in PIN-464: the cart stays locked after the expired notice, and we leave it.**
  When Place order is refused with `PLACEMENT_ABANDONED`, the Back to cart link in the expired
  notice in `CheckoutPage.tsx` doesn't call `leaveCheckout`. If reloads kept the session alive
  past `max-age`, `/cart` refuses changes with `CART_LOCKED` for up to 30 minutes. The
  customer gets out by starting checkout again and pressing the review's Back to cart button.
  Unlocking on that refusal could break an order in flight from another tab on the same
  session. Revisit if support sees it.
- **Decided in PIN-464: the scope of `orders_cancelled_total` waits.** It counts only the
  job's cancels. Cancels from `PlaceOrderUseCase.cancel`, when the cart refuses, and customer
  cancels ([ADR-0063](0063-customer-order-cancellation.md)) never appear, so a dashboard
  reading it as all cancelled orders undercounts and `reason` only ever has one value. Fixing
  it means renaming a metric this ADR documents, or giving `PlaceOrderUseCase` a dependency.
  Decide when the metric is first put on a dashboard or alert.
