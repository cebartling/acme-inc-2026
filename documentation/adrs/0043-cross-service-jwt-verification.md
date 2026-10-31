# ADR-0043: Cross-Service JWT Verification with JWKS

## Status

Accepted

Builds on [ADR-0035](0035-jwt-token-implementation.md), which defines the RS256 token format.

## Context

Identity signs RS256 access tokens and sets them in the HttpOnly, Secure, SameSite=Strict
`access_token` cookie (`AuthCookieBuilder`). Until US-0004-08 only identity could check them.
The shopping cart needed to know which customer is calling, so a signed-in customer's cart
follows them to any device and a guest cart can merge into it on sign-in.

Identity's signing keys live in memory in `SigningKeyProvider`. It keeps the current key and
up to three rotated-out keys (`PREVIOUS_KEY_CAPACITY`), rotates on a daily check once
`identity.jwt.key-rotation-period-days` (30) has passed, and generates a new key on every
restart. Its own comment calls the restart behaviour a "CRITICAL LIMITATION". ADR-0035
describes key IDs as `key-YYYY-MM` and rotation "without invalidating existing tokens"; the
code adds a random suffix (`key-YYYY-MM-xxxxxxxx`) so two rotations in a month differ, and a
restart drops every key.

## Decision

Services verify identity's access tokens **locally against a published JWKS**:

1. **Identity publishes** `GET /.well-known/jwks.json` (`JwksController`). It returns the
   public halves of `SigningKeyProvider.getVerificationKeys()`, current key first, with
   `kid`, `use=sig` and `alg=RS256`. Private keys never leave identity.
2. **The cart verifies with Spring's OAuth2 resource server** (`SecurityConfig`), configured
   under `spring.security.oauth2.resourceserver.jwt` with `jwk-set-uri`, `issuer-uri` and
   `audiences` taken from the same `JWT_ISSUER` and `JWT_AUDIENCE` variables as identity.
   It checks the signature, `exp`, `iss` and `aud`.
3. **`accessTokenValidator` adds two checks:** `token_use` must be `access`, since identity
   signs refresh tokens with the same key, and `sub` must be a UUID.
4. **The token is read only from the `access_token` cookie.** The cart's
   `BearerTokenResolver` ignores the `Authorization` header.
5. **Every cart endpoint stays open to guests.** No token means the session's cart. A token
   that fails is a 401: `{"error":"TOKEN_EXPIRED"}`, which the customer app answers by
   refreshing and retrying, or `{"error":"INVALID_TOKEN"}`.
6. **CSRF protection is off** in the cart. The `SecurityConfig` comment gives the reasons:
   it is a JSON API, state-changing requests need a JSON content type, so a cross-site form
   can't send one without a CORS preflight, and the cookie is SameSite=Strict.
7. **Verifiers refetch the set on an unknown `kid`** rather than caching it forever
   (`JwksController` comment, cart `application.yml`), because identity's keys change on
   restart.

The customer service verifies the same way for everything under `/api/v1/customers/` (PIN-428),
and takes the user from the token rather than an `X-User-Id` header. The lookups by ID, user ID,
number and email return a whole customer and no page uses them, so they take a service's
`X-Service-Token` instead (`ACME_CUSTOMER_SERVICE_TOKEN`, the pattern of
[ADR-0061](0061-order-service-token-for-cart-calls.md)). The notification service sends it.

## Consequences

### Positive

- The cart answers without calling identity per request, and keeps answering guests while
  identity is down.
- Verification is configuration plus one validator, and a new service can copy it. The
  order service plans to add it this way (`ARCHITECTURE.md`, Order Service, PIN-366).
- A refresh token can't be used as an access token in the cart.

### Negative

- Every identity restart invalidates every issued token, in every verifier. Signed-in
  customers get a 401 in the cart until they sign in again.
- Local verification can't see identity's session state. A token stays valid in the cart
  until `exp`, 15 minutes after issue (`access-token-expiry-minutes`), even after sign-out.
- The checks differ between services. Identity's `TokenService.parseAccessTokenClaims` does not
  check `aud`, and accepts a token with no `token_use`; the cart requires both.
- The cart's 401 bodies have only `error`, not the `{error, code}` shape of its other errors
  ([ADR-0050](0050-api-error-body-format.md)).
- With CSRF off, the cart relies on SameSite and its CORS origin list.

### Mitigations

- Keep the restart limitation documented here, in `SigningKeyProvider` and in
  `ARCHITECTURE.md` (Token Security) until keys are persisted, e.g. in Vault (ADR-0009).
- The 15-minute access token lifetime bounds how long a signed-out token works.
