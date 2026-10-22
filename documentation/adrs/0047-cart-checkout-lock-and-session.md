# ADR-0047: Cart Checkout Lock and Checkout Session Lifecycle

## Status

Accepted

## Context

PIN-291 (cart checkout transition: validate, lock and convert the cart) was split into
stories: start checkout (PIN-329), resume, leave and unlock (PIN-330), read a session for
the order service (PIN-365), convert into an order (PIN-331) and the unlock grace period
(PIN-383). The order service builds an order from the cart, so the cart must not change
underneath it, and a customer who walks away must get a usable cart back.

## Decision

The cart itself is locked for checkout, and a checkout session on the `carts` row says for
how long.

1. **Statuses.** `CartStatus` adds `CHECKOUT` (V6) and `CONVERTED` (V9). `ACTIVE → CHECKOUT`
   on start, `CHECKOUT → ACTIVE` on leave or unlock, `CHECKOUT → CONVERTED` on convert.
   `CONVERTED` is final. `CartStatus.CURRENT` is `ACTIVE` and `CHECKOUT`, so a locked cart is
   still the owner's current cart and the partial unique indexes keep a second one out.
2. **Locked, not hidden.** `Cart.addItem`, `updateItemQuantity`, `removeItem` and `clear`
   refuse a `CHECKOUT` cart with 409 `CART_LOCKED`. `GET /carts/current` still returns it.
3. **Start validates.** `StartCheckoutUseCase` refuses an empty cart (422 `CART_EMPTY`),
   checks stock per distinct variant ([ADR-0045](0045-cart-product-calls-fail-closed.md)),
   and refuses with 422 `CART_VALIDATION_FAILED` and per-line `validationErrors` if any line
   can't be ordered. The lock is taken in a transaction that requires the cart's version to
   match the one that was checked ([ADR-0048](0048-cart-optimistic-locking.md)).
4. **A 30-minute sliding session.** `checkout_session_id` and `checkout_expires_at` (V6) are
   set from `StartCheckoutUseCase.SESSION_LENGTH`; `ck_carts_checkout_session` (V8) requires
   both on a `CHECKOUT` row. Starting again while the session is live resumes it: same ID, no
   re-check, no event, and the expiry moves to 30 minutes from now. Viewing the cart does
   not extend it, since the header badge reads the cart on every page.
5. **Leaving and lapsing.** `DELETE /carts/{cartId}/checkout` makes the cart `ACTIVE` and
   publishes `CheckoutAbandoned`; leaving a cart not in checkout changes nothing. A lapsed
   session is unlocked by `UnlockLapsedCheckoutsUseCase` only after
   `acme.cart.checkout-unlock.grace` (default `PT2M`, PIN-383), so an order placed as the
   session lapsed can still convert ([ADR-0049](0049-cart-data-lifecycle-jobs.md)). Until
   then the session reads as not live (404 `CHECKOUT_SESSION_NOT_FOUND`), but the cart stays
   locked.
6. **Each lapse is reported once.** Whichever path ends a lapsed session (the unlock job,
   leaving, or starting again) reports it through `CheckoutSessionExpiry`, publishing
   `CheckoutSessionExpired` and counting `cart.checkout.expired`. A conversion is not an
   expiry.
7. **Idempotent convert.** `POST /carts/{cartId}/convert {orderId, checkoutSessionId}` runs
   `Cart.convertToOrder`. The cart is found by ID and owner in any status
   (`findOwnedCartInAnyStatus`), so a repeat for the same order and session answers the same
   and saves and publishes nothing. A different order or session is 409
   `CART_ALREADY_CONVERTED`; a cart not in checkout for that session is 404
   `CHECKOUT_SESSION_NOT_FOUND`. `ck_carts_converted_order` and `uq_carts_order` (V9) back
   this in the schema.

## Consequences

### Positive

- The order service reads and converts exactly the cart that was validated; nothing can
  change it in between.
- Retrying convert is safe, and leaving checkout twice is not an error.
- An abandoned checkout frees itself without the customer doing anything.

### Negative

- There is no stock reservation. A line in stock at start can sell out before the order is
  placed.
- Only starting again extends the session today; later checkout steps are expected to, but
  nothing else does yet.
- A lapsed cart stays locked for the grace period plus up to one job interval. Changes in
  that gap are 409 `CART_LOCKED`, though leaving or restarting checkout work.
- An order slower than the grace period, or a customer who leaves checkout mid-order, leaves
  the order with no converted cart: convert is refused once the cart has moved on.
- Lock state lives in columns on `carts`, not in a separate session table, so a cart holds at
  most one checkout session.

### Mitigations

- Every unlock and expiry bumps the version, so a request holding the locked copy can't save
  it back.
- `CheckoutSessionExpired`, `CheckoutAbandoned` and `CartConvertedToOrder` record how each
  session ended.
