# ADR-0048: Optimistic Locking with One Retry for Cart Changes

## Status

Accepted

## Context

A cart can be changed from two tabs at once, or by a request and a scheduled job at once.
Before PIN-278, a PATCH racing a DELETE of the same line ended in a 500, and an add that
loaded a cart just before the expiry job expired it could save it back as `ACTIVE` (a race
PIN-287 had accepted). Cart changes are short and conflicts are rare, so the service needed
to detect a lost race, not prevent one.

## Decision

1. **Version only the aggregate root.** V5 adds `carts.version`, mapped with `@Version` on
   `Cart`. `CartItem` is not versioned. Every change goes through `Cart.touch()`, which sets
   `updated_at` and `last_active_at`, so the row and its version move even when only a line
   changed. The version is null until first saved, because IDs are assigned in code and a
   null version is how Spring Data tells a new cart from an existing one.
2. **Retry once, against a fresh read.** `retryOnConflict` (`CartSupport.kt`) runs a change
   and, on `ObjectOptimisticLockingFailureException`, runs it once more. The retried block
   re-reads everything it needs; with `spring.jpa.open-in-view` off (PIN-296), that read is
   the committed cart. Add and update retry only the transaction, since a line's price does
   not depend on the cart. Remove, clear, merge, start checkout, leave checkout and convert
   rerun their whole operation.
3. **A unique-key race is the same conflict.** Hibernate inserts new rows before it checks
   the version, so two first adds that each create a cart lose on `uq_carts_active_session`
   or `uq_carts_active_user`, and two adds of one new variant lose on
   `uq_cart_items_cart_variant`. A `DataIntegrityViolationException` whose SQLState is
   `23505` is rethrown as an optimistic-lock conflict. Its message names the key, which can
   be the session ID, so it is not copied.
4. **A second conflict is a 409.** `CartController.cartConflict` answers 409 `CART_CONFLICT`
   and logs a warning with the exception class only. The customer app reloads the cart and
   shows the message.
5. **Jobs bump the version; activity does not.** The expiry and unlock UPDATEs set
   `version = version + 1`, so a request holding the old copy can't save it back
   ([ADR-0049](0049-cart-data-lifecycle-jobs.md)). `touchGuestCart` deliberately leaves the
   version alone, so a page view in one tab never conflicts with a change in another.
6. **Events after commit.** Events are published after the transaction, so a failed attempt
   publishes nothing.

## Consequences

### Positive

- No lost updates and no revived carts, without holding row locks across a request.
- The common race, a PATCH against a DELETE of the same line, resolves on retry as the 404
  `CART_ITEM_NOT_FOUND` the cart page already reloads on quietly.
- One rule covers both kinds of race, a stale version and a duplicate insert.

### Negative

- Any change to a cart conflicts with any other, even on different lines, since only the
  root is versioned.
- Start checkout raises the conflict itself when the version moved since its stock check,
  so a busy cart can fail checkout with `CART_CONFLICT`.
- Merge also takes a pessimistic row lock on the guest cart
  ([ADR-0046](0046-guest-cart-identity-and-merge.md)), so the cart uses two locking styles.
- The shopping cart is the only backend service that uses JPA `@Version`; the others have no
  equivalent pattern to follow or share code with.

### Mitigations

- Conflicts that the retry absorbs are logged at INFO under
  `com.acme.cart.application.CartConflicts`, so their rate can be watched.
- `SchemaValidationTest` pins the mapping, and `CartRepositoryIntegrationTest` runs the
  PIN-278 races against Testcontainers Postgres.
