# ADR-0063: Customers Can Cancel Submitted Orders, and Those Orders Stay Visible

## Status

Accepted

## Context

Until now a `CANCELLED` order was always a failed placement: the cart refused it, or the
abandoned order job cancelled it (ADR-0062). Those are hidden from the customer, because they
were told the order didn't succeed. A cancel code implied the same thing.

Customers now need to cancel a `SUBMITTED` order (PIN-429, epic F3), and an order they cancelled
did succeed, so they should keep seeing it. `CANCELLED` alone can no longer say which kind of
order it is.

## Decision

1. **`cancelled_by` says who cancelled.** It is `CUSTOMER` or `PLACEMENT`. V6 backfills existing
   cancelled orders as `PLACEMENT`, and `markCancelled` sets it for every failed placement. This
   replaces the implicit "a cancel code means a failed placement" rule.
2. **Visibility:** a customer sees `status = SUBMITTED OR (status = CANCELLED AND cancelled_by =
   CUSTOMER)`, in both the single-order read and the list. This revises PIN-367's "only
   `SUBMITTED` orders are visible".
3. **Cancelling is a conditional update.** `markCancelledByCustomer` moves the caller's order
   `SUBMITTED → CANCELLED`, and `OrderCancelled` goes to the outbox in the same transaction, only
   if that update changed a row. Repeats and racing requests write one event.
4. **Repeats answer `200`.** The endpoint reads the order back through the visibility rule, so a
   customer who already cancelled gets the same order. Anything else is `404 ORDER_NOT_FOUND`.
   That includes an order the update found `PENDING` that has since become `SUBMITTED`, which
   wasn't cancelled.
5. **A JSON body with a reason is required**, 500 characters at most, which is what
   `SecurityConfig`'s CSRF reasoning depends on for state-changing requests. The reason is stored
   and sent in the event but not logged, because it is free text from the customer.
6. **`cancelled_by` isn't tied to `status` by a constraint.** An instance still running the old
   code during a deploy cancels placements without it, and that write must not fail. Such an
   order has a null `cancelled_by`, so it stays hidden, as a failed placement should.

## Consequences

- `GET /by-idempotency-key` answers 404 for a customer-cancelled order, like any cancelled one.
  There's nothing for a client that lost an answer to resume.
- Retrying `POST /api/v1/orders` with a customer-cancelled order's key answers `404
  CHECKOUT_SESSION_NOT_FOUND`, because customer cancels have no `cancel_code`. Its message, "The
  order was not placed", isn't accurate there, but the retry places nothing.
- A placement retry in flight while the customer cancels the same order logs
  `PlaceOrderUseCase`'s "no cart and no order" error, which isn't true in that case. It needs a
  retry racing a cancel, so it stays as it is.
- `idx_orders_user_status_created` may no longer serve the list's sort because of the `OR`.
  Postgres then sorts one customer's orders, which is a small set.
- `orders_cancelled_total` doesn't count customer cancels. PIN-464 holds the decision on that
  metric's scope.
- Refusing to cancel after fulfilment starts follows from the status: only `SUBMITTED` orders can
  be cancelled. Refunds and inventory release come with the Payment and Inventory epics.
