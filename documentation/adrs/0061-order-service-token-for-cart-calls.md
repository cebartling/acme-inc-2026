# ADR-0061: The Order Service Authenticates to the Cart with a Shared Secret

## Status

Accepted

Amends [ADR-0044](0044-forward-customer-credentials-between-services.md): the order service
still forwards the customer's credentials, and now also proves who it is. This replaces
ADR-0044's decision 3, which left service-to-service authentication out of scope, and
qualifies decision 2: these two endpoints now check the caller as well as the owner.

Amended in PIN-491: a third endpoint, the conversion read, takes the service token alone
(decision 5).

## Context

Under ADR-0044, the cart authorizes the order service's calls only by owner, so it can't tell
the order service from the customer's browser. CORS allows the customer app with
credentials and CSRF is off. So the customer, or any script running as them, could call
`POST /api/v1/carts/{cartId}/convert` with a made-up `orderId` during their own checkout.
The cart would become `CONVERTED` with no order behind it, and `CartConvertedToOrder` would be
published (through the outbox since ADR-0060, so it always is). The real order's convert
would then be 409 `CART_ALREADY_CONVERTED` (PIN-382). `GET
/api/v1/carts/checkout-sessions/{id}` had the same exposure, for reads.

No service in the repo has its own identity: no mTLS, no client-credentials grant in
identity, and no secret shared between services.

## Decision

1. **A shared secret.** The order service sends `X-Service-Token: <secret>` on its two cart
   calls, alongside the customer's `access_token` cookie. Both services read the secret from
   `ACME_ORDER_SERVICE_TOKEN` (`acme.cart.order-service-token` in the cart,
   `acme.cart.service-token` in the order service). It has no default, and either service
   refuses to start without it, or with leading or trailing whitespace, which the cart's
   server trims off the header and so could never match.
2. **Only these two endpoints require it, until decision 5.** `convert` and the checkout-session read are for
   the order service only. No frontend calls them. Every other cart endpoint stays open to
   the customer, as before.
3. **Checked first, then ownership.** The cart's `ServiceCredentials` compares the header in
   constant time (`MessageDigest.isEqual`). Missing or wrong is a 403
   `{error, code: "SERVICE_CREDENTIALS_REQUIRED"}` ([ADR-0050](0050-api-error-body-format.md)),
   before the handler reads or changes the cart (the guest session interceptor has already
   run, and request validation comes first). With the header, the existing owner checks still apply, so
   the order service reaches only the customer's own cart.
4. **The check is in the controller**, not in `SecurityConfig`, which keeps its "every
   endpoint open, token optional" setup (ADR-0043).
5. **The conversion read needs the service token and no owner (PIN-491).** `GET
   /api/v1/carts/{cartId}/conversions/{orderId}` answers whether the cart converted for that
   order, for the abandoned-order job ([ADR-0062](0062-cancel-abandoned-orders.md)), which has
   no customer token. It is checked like decision 3, token first, but with no owner check. It
   answers only for a matching cart and order ID pair, both unguessable UUIDs, returns no cart
   contents, and changes nothing; every other case is 404 `CART_NOT_CONVERTED`. Someone with
   the secret could already convert any cart they hold a token for, so it adds little. Anyone
   with the secret and both IDs can confirm a conversion, which is the cost of having no
   owner to check.

## Alternatives Considered

- **A client-credentials JWT from identity**, verified against the JWKS the cart already
  uses. It would fit the existing token setup, but needs a new grant and a service-account
  model in identity. Identity also regenerates its keys on every restart (ADR-0043), so the
  order service would have to fetch a new token after each one.
- **mTLS between services.** Too much certificate setup for two endpoints.

## Consequences

### Positive

- The customer can no longer convert their own cart or read their checkout session
  directly. PIN-382 is fixed.
- Small and explicit: one header, one environment variable, no change to identity.

### Negative

- The secret is static. Changing it means changing both services at once, and anyone with
  the environment can act as the order service. It isn't in Vault (ADR-0009) yet.
- Local development and the acceptance tests use a known default,
  `local-dev-order-service-token`, from `docker-compose.apps.yml`.
- A wrong secret in the order service makes every order fail with "cart unavailable" until
  it's fixed. `CartClient` logs the 403 as an error naming the setting, so it isn't mistaken
  for a cart outage.
- The check runs in the handler, so a refused call still passes through
  `GuestSessionInterceptor` (a guest cookie touches the cart's `last_active_at` and is
  re-issued), and a malformed body or path is a 400 before the 403.
- It doesn't address ADR-0044's other negatives, such as a forwarded access token expiring.
