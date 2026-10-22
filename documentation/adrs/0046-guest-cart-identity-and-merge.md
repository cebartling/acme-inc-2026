# ADR-0046: Guest Cart Identity and Merge on Sign-In

## Status

Accepted

## Context

Shoppers add to a cart before they sign in (US-0004-06), and expect it to follow them once
they do (US-0004-08). The shopping cart service needs a way to recognise a guest across
requests, a rule for who owns a cart, and a way to fold a guest cart into an account cart
that is safe to repeat and to run twice at once.

## Decision

1. **The server mints the guest session.** `CartController.addItem` generates a UUID when the
   caller has neither a valid access token nor a valid session, and returns it only on a
   successful add. Nothing else mints one: a first-time visitor's `GET /carts/current` is a
   204 and sets no cookie. `GuestSessionCookies` is the only place the cookie is built:
   `acme_session_id`, HttpOnly, `SameSite=Lax`, path `/`, `Max-Age` of `acme.cart.guest-ttl`
   (default `30d`), and `Secure` unless `ACME_CART_COOKIE_SECURE=false`. A cookie value that
   is not a canonical UUID string is ignored, and the next add replaces it.
2. **Sliding expiry in one interceptor.** `GuestSessionInterceptor`, registered on
   `/api/v1/carts/**` by `GuestSessionConfig`, re-issues the cookie with a fresh TTL on every
   guest request that maps to a cart endpoint, before the handler runs, so a 400 is covered
   too (PIN-288). It also calls `CartRepository.touchGuestCart`, so extending the cookie and
   recording activity always happen together
   ([ADR-0049](0049-cart-data-lifecycle-jobs.md)). Signed-in and `OPTIONS` requests are left
   alone.
3. **One owner, one current cart.** A `Cart` has exactly one of `session_id` or `user_id`
   (the access token's `sub`), enforced by `ck_carts_one_owner` (V2) and `Cart`'s `init`.
   Partial unique indexes `uq_carts_active_session` and `uq_carts_active_user` allow one
   `ACTIVE` or `CHECKOUT` cart per owner (V6). A signed-in caller always acts on the user
   cart; ownership comes from the token or cookie, never from the `cartId` in the URL.
4. **Merge on sign-in.** `POST /api/v1/carts/merge` requires a signed-in caller (401
   `SIGN_IN_REQUIRED`) and takes the guest cart from the HttpOnly cookie, never the body.
   `MergeCartsUseCase` prices the guest variants first, outside the transaction
   ([ADR-0045](0045-cart-product-calls-fail-closed.md)). In the transaction it re-reads the
   guest cart with `findForUpdateBySessionIdAndStatus` (`PESSIMISTIC_WRITE`, a
   `SELECT … FOR UPDATE`), then `Cart.absorb` sums lines per variant, caps each at
   `acme.cart.max-order-quantity` (default 10), reprices changed lines, and marks the guest
   cart `MERGED`.
5. **Idempotent, and honest about adjustments.** A missing, empty or already `MERGED` guest
   cart is a no-op with no event. Capped variants are listed in
   `mergeResult.quantitiesAdjusted`; a variant the product service answers
   `VARIANT_NOT_FOUND` for is left behind in the `MERGED` guest cart and listed in
   `itemsUnavailable` (PIN-306). A guest cart in checkout is not merged, and an account cart
   in checkout refuses the merge with 409 `CART_LOCKED`.
6. **The cookie survives sign-in.** After sign-out the same browser starts a new guest cart,
   which the partial indexes allow beside the `MERGED` one.

## Consequences

### Positive

- JavaScript can neither read nor forge the session ID, and the service only accepts IDs it
  could have minted.
- An active shopper's guest cart never expires under them, and no endpoint has to remember
  to refresh the cookie.
- Two concurrent merges of one guest cart run in turn; the second finds no `ACTIVE` guest
  cart and does nothing.

### Negative

- The session ID is the only key to a guest cart. Anyone holding the cookie holds the cart,
  so the service never logs it.
- An expired cookie can't be recovered: the next add starts a new session and cart, and the
  old lines are not restored.
- A merge fails as a whole on any pricing failure other than `VARIANT_NOT_FOUND`; the
  customer app logs it and carries on, leaving the guest cart unmerged.
- Merge mixes a pessimistic row lock on the guest cart with the optimistic version check on
  the account cart ([ADR-0048](0048-cart-optimistic-locking.md)).

### Mitigations

- `cart.session.new_cart` (`cart_session_new_cart_total`) counts returning sessions that got
  a fresh cart, by cart ID only.
- The customer app awaits `mergeCartAfterSignIn` before navigating and shows capped or
  dropped lines in `CartMergeNotice`.
