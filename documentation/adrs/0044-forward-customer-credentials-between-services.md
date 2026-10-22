# ADR-0044: Service-to-Service Calls Carry the Customer's Credentials

## Status

Accepted

Relates to [ADR-0043](0043-cross-service-jwt-verification.md), which covers how the cart
verifies the customer's token.

## Context

Placing an order (PIN-366) needs two calls from the order service to the shopping cart.
PIN-366 records that converting the cart is a synchronous call, not an event the cart
consumes:

- **Read the checkout session's cart** (PIN-365):
  `GET /api/v1/carts/checkout-sessions/{checkoutSessionId}` returns the session ID and
  `expiresAt`, the cart ID, `userId` (null for a guest), the lines and the summary.
- **Convert the cart** (PIN-331): `POST /api/v1/carts/{cartId}/convert`
  `{orderId, checkoutSessionId}` marks the cart `CONVERTED` with the order's ID. Converting
  again into the same order for the same session answers the same, so the caller can retry.

Both are ordinary `CartController` endpoints. Like every cart endpoint, they resolve the
caller with `ownerOf(jwt, sessionCookie)`: the verified `access_token`'s subject, else a
valid `acme_session_id` guest cookie. The cart then acts only on that owner's cart
(`findActiveCart`, `findOwnedCartInAnyStatus`). Someone else's cart, or no credentials, is a
404, the same as a missing one.

The services have no service identity. Product and order have no Spring Security at all,
and the cart's calls to product (`ProductPricingClient`, `ProductAvailabilityClient`) send
no credentials. There is no mTLS or client-credentials token anywhere in the repo.

The order service has no endpoints or cart client yet; placing an order is PIN-366.

## Decision

**A service that acts for a customer forwards that customer's credentials:**

1. The order service sends the customer's `access_token` cookie, or the guest's
   `acme_session_id` cookie, on its calls to the cart.
2. The cart authorizes those calls the same way as the browser's: by owner, not by caller.
   It reads no `Authorization` header, so a bearer token is treated as anonymous and gets
   the 404 (`ARCHITECTURE.md`, Reading a checkout session).
3. Service-to-service authentication was left out of scope for PIN-365 and PIN-331. Any
   endpoint that only a service should call needs its own decision.

## Consequences

### Positive

- No new credentials, key distribution or trust setup: the cart's existing ownership checks
  cover the order service's calls.
- The order service can only ever reach the cart of the customer placing the order.

### Negative

- The cart can't tell the order service from the customer's browser. CORS allows the
  customer app with credentials and CSRF is off, so the customer, or any script running as
  the customer, can call `convert` with a made-up `orderId` and their live session. The cart
  becomes `CONVERTED` with no order behind it, `CartConvertedToOrder` is published, and the
  real convert is 409 `CART_ALREADY_CONVERTED` (PIN-382). Reading the checkout session has
  the same exposure, for reads.
- The order service handles customer credentials, so it must not log or store them.
- A forwarded access token expires 15 minutes after issue. A call made with an expired
  token is a 401 `TOKEN_EXPIRED`, and the order service has no refresh token to fix it.
- A forwarded guest cookie is treated as guest activity: `GuestSessionInterceptor` touches
  the cart's `last_active_at` and answers with a `Set-Cookie` the order service must drop.
- Work that has no customer request behind it, such as a retry job, has no credentials to
  forward.

### Mitigations

- PIN-382, in the backlog, is to accept `convert` only from a caller authenticated as the
  order service, still scoped to the customer's cart, and could cover the checkout session
  read too.
- `convert` is idempotent for the same order and session, so the order service can retry it
  while the customer's credentials are still valid.
