# ADR-0062: Abandoned Orders Are Cancelled, Unless the Cart Converted for Them, and Then They Are Submitted

## Status

Accepted

Amended in PIN-464: decision 3 committed to a service-token cart read (PIN-491) instead of
declining it. Amended in PIN-491: the read is built, so the job finishes a lost convert instead
of cancelling it (decisions 1 and 3). [ADR-0061](0061-order-service-token-for-cart-calls.md) is
amended too, because the read has no customer token.

## Context

Placing an order saves it `PENDING`, converts the cart, then marks it `SUBMITTED`. If the cart
doesn't answer, the customer gets `503 CART_UNAVAILABLE`, and the order stays `PENDING` until
they retry with the same `Idempotency-Key`. Nothing cleaned up an order whose retry never came
(PIN-427).

A scheduled job can't convert the cart. Converting forwards the customer's access token
(ADR-0044, ADR-0061), the cart finds the cart's owner from that token, and a job has no token.
It can finish a placement whose convert already happened, though (decision 3).

A convert can also succeed without the order hearing of it. The service can crash before
`markSubmitted`, or the cart can commit the convert but answer after the 2-second read timeout,
so the customer gets `503 CART_UNAVAILABLE` and may never retry. The timeout case needs no
crash, so it's more than an edge case (PIN-464). Either way the order is
`PENDING` and its cart is already `CONVERTED`. Before PIN-491 the job couldn't see this,
because every cart read needed a customer token. Decision 3's read doesn't.

## Decision

1. **The job cancels, unless the cart converted for the order (PIN-491).**
   `CancelAbandonedOrdersUseCase` moves `PENDING` orders older than
   `acme.order.abandoned.max-age` (default one hour) to `CANCELLED` with `PLACEMENT_ABANDONED`,
   a bounded run (see below), after the cart read of decision 3 says the cart did not convert
   for the order. It uses the conditional
   `OrderRepository.markCancelled`, so a racing retry leaves the order either `SUBMITTED` or
   `CANCELLED`. These orders stay hidden from the customer, like other failed placements
   (recorded as `cancelled_by = PLACEMENT` since [ADR-0063](0063-customer-order-cancellation.md)).
2. **A late retry is refused for good.** The same key answers 404 `PLACEMENT_ABANDONED`
   through `PlaceOrderUseCase.resume`, and the customer app treats that as an expired
   checkout.
3. **A lost convert is finished from the cart (PIN-491).** Before cancelling, the job asks the
   cart `GET /api/v1/carts/{cartId}/conversions/{orderId}`. PIN-464 reversed the original choice
   to skip this read: a customer who loses both cart and order is worse than the cost of a new
   endpoint. The cart answers 200 `{cartId, status, orderId, convertedAt}` if it is `CONVERTED`
   for that order, and 404 `CART_NOT_CONVERTED` for anything else (unknown cart, another status,
   another order), so the read reveals nothing about other carts. Authorization is the service
   token alone ([ADR-0061](0061-order-service-token-for-cart-calls.md), amended): the job has no
   customer token, so the owner check on the cart's other two order-service calls can't apply.
   - **Converted:** the job submits the order through `OrderSubmitter`, the same conditional
     `PENDING → SUBMITTED` update and `OrderCreated` outbox write as `PlaceOrderUseCase`. It logs
     a warning and counts `orders_recovered_total{reason=LOST_CONVERT}` only when this call
     moved the order (`SubmitResult.Submitted`); an order a retry submitted first is not
     counted, and one a racing caller cancelled is logged as an error by `OrderSubmitter`.
   - **Not converted:** the order is cancelled, logged and counted as before. The cart keeps the
     order's ID on a `CONVERTED` cart (`carts.order_id`) for 90 days (`ACME_CART_RETENTION`),
     far longer than `max-age`, so a purged cart can't be mistaken for "not converted".
   - **Cart can't be asked** (timeout, 5xx, a refused token, or any refusal but
     `CART_NOT_CONVERTED`): the order stays `PENDING` and the next run tries again. Cancelling on a guess could lose a converted cart.
   - **A run is bounded and can't be starved (PIN-491).** It pages through the abandoned
     orders oldest first by `(createdAt, id)`, `batch-size` at a time and at most 10 pages, so
     orders left `PENDING` don't hide the ones behind them. It stops early when every order on
     a page got no answer from the cart (down, or refusing the token), because walking the
     backlog would only wait out timeouts. One order throwing is logged as an error and
     skipped; the rest of the batch is still settled.
4. **The minimum age is a constant.** The service refuses to start unless `max-age` is over
   33 minutes: the cart's 30-minute checkout session, 2-minute unlock grace and 1-minute unlock
   interval. The order service can't read the cart's settings, so these are the cart's
   defaults.

## Consequences

- A customer whose convert was lost gets the order submitted after `max-age`, not at once. They
  were told 503, so the order appearing in their list later is a surprise, but not a loss.
- While the cart is down, abandoned orders stay `PENDING` past `max-age` and are retried every
  run until the cart answers.
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
  of on `createdAt`, which changes decision 1's age rule. The cart check (PIN-491) narrows the
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
