# ADR-0050: API Error Response Body Format

## Status

Accepted

Relates to [ADR-0021](0021-information-disclosure-prevention.md), which keeps system internals
out of error messages.

## Context

Each service shapes its error bodies its own way:

- **Customer** answers every error with `ErrorResponse` (`api/v1/dto/ErrorResponse.kt`):
  `{timestamp, status, error, message, path, details}`. `error` is the HTTP reason, such as
  `"Bad Request"` or `"Validation Failed"`, and `details` lists field errors with their
  `rejectedValue`.
- **Identity** has a shared `ErrorResponse` `{error, message, timestamp, details}`, used by
  its `GlobalExceptionHandler`, plus per-endpoint DTOs that add fields, such as
  `SigninErrorResponse` (`remainingAttempts`, `lockedUntil`, `retryAfterSeconds` and more)
  and `MfaVerifyErrorResponse`. Here `error` is the machine code, e.g. `INVALID_REQUEST`, and
  `message` is the text.
- **Product** (`GlobalExceptionHandler`) and **shopping cart** (`@ExceptionHandler` methods
  and `errorResponse` in `CartController`) answer `{"error": message, "code": CODE}`. Here
  `error` is the text and `code` is the machine code. The cart adds `maxQuantity` to a
  max-quantity error and `validationErrors` to a checkout refusal.

Cart and product need the code. `CART_ITEM_NOT_FOUND` and `VARIANT_NOT_FOUND` are both 404s,
and the cart page reloads quietly for one and explains the other. The cart's
`ProductPricingClient` drops a line on a sign-in merge only for product's own
`VARIANT_NOT_FOUND`, never for a gateway's 404 (PIN-306).

PIN-303 and PIN-305 made a body that fails `@Valid`, can't be read, or has a path or query
parameter of the wrong type a 400 `INVALID_REQUEST` whose message names only the field, e.g.
`"Invalid request: quantity"`. It never echoes Jackson's or the parser's message, which
holds class names and parser internals.

## Decision

1. **`{"error": <human-readable message>, "code": <UPPER_SNAKE_CASE code>}`** is the error
   body for new services. Clients branch on `code`, never on `error` or the status alone.
   PIN-366 already specifies "the documented `{error, code}` body" for the order service.
2. **Extra fields sit beside them** when a client needs them, as `maxQuantity` and
   `validationErrors` do in the cart.
3. **A request that can't be bound is a 400 `INVALID_REQUEST` naming only the field.**
   Framework and parser messages are logged at debug, not returned.
4. **Customer and identity keep their formats.** This ADR records them; it does not change
   them.

## Consequences

### Positive

- A client of cart, product or order reads one shape and one field for the code.
- A service caller can tell a service's own error from a gateway's or a wrong URL's.
- Binding errors don't leak framework internals, in line with ADR-0021.

### Negative

- Three formats remain. `error` means a message in cart and product, a machine code in
  identity, and the HTTP reason in customer.
- Cart and product don't fully follow it yet. The cart's 401s are `{"error":"TOKEN_EXPIRED"}`,
  `{"error":"INVALID_TOKEN"}` (`SecurityConfig`) and `{"error":"SIGN_IN_REQUIRED"}` (merge),
  with the code in `error` and no `code`. Product's missing-product 404 has no `code`.
- Product's 400 for an `IllegalArgumentException` returns that exception's own message.
- Customer returns a field's `rejectedValue`, and its `IllegalArgumentException` handler
  returns the exception's message.

### Mitigations

- `ARCHITECTURE.md` documents each service's error bodies (Shopping Cart and Product, Error
  bodies), so a client author can find the shape.
- Aligning cart's 401s and product's 404 with `{error, code}` is a small change to make
  when a story touches them.
