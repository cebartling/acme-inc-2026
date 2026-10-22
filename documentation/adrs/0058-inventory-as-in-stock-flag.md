# ADR-0058: Inventory as an In-Stock Flag Without Reservation

## Status

Accepted

Related to [ADR-0045](0045-cart-product-calls-fail-closed.md) and
[ADR-0047](0047-cart-checkout-lock-and-session.md).

## Context

Epic 003, Product Inventory Management (PIN-56,
`documentation/epics/003-product-inventory.md`), describes a separate inventory service
with multi-location stock, reservations through the order lifecycle and low-stock alerts.
None of it is built.

The stories that needed stock came first. US-0004-10, Out of Stock Handling (PIN-273),
sketched an inventory response of `{status: "OUT_OF_STOCK", available: 0}`. The add-to-cart
story deferred its "Only N available" criterion (AC-0004-06-11) to PIN-273 because "stock
is a boolean today" (PIN-266). PIN-273 then shipped the response as
`{variantId, availability}`, noting that "the service knows no quantities" (`84b3082`).
The first order story leaves inventory reservation out of scope (PIN-366).

## Decision

Stock is one boolean per variant, owned by the product service, with no quantities and no
reservation.

1. **The flag.** `product_variants.in_stock BOOLEAN NOT NULL DEFAULT TRUE`
   (`V3__add_product_variants.sql`, `ProductVariant.inStock`). There is no quantity column.
   Product has no endpoint that changes it; the seed migrations set it.
2. **The lookup.** `GET /api/v1/inventory/availability/{variantId}` (`InventoryController`)
   answers `IN_STOCK` or `OUT_OF_STOCK`, and 404 `VARIANT_NOT_FOUND` for an unknown variant
   or an archived product's (PIN-306). A product is in stock when it has no variants or any
   variant is in stock (PIN-318), and search results carry `inStock` (PIN-273).
3. **Where it is checked.** The customer app checks before it posts an add to cart, and the
   `/cart` page checks each line (PIN-273). The cart service checks only at checkout:
   `StartCheckoutUseCase` asks `ProductAvailabilityClient` about each distinct variant, and
   refuses with 422 `CART_VALIDATION_FAILED` for an out-of-stock or gone line, or 503
   `AVAILABILITY_UNAVAILABLE` when the product service can't answer (PIN-329).
4. **No reservation.** A passed check holds nothing. Locking the cart for checkout locks
   the cart's contents, not stock.

## Consequences

### Positive

- One column and one read endpoint cover every stock question the built stories ask.
- Checkout fails closed: it never locks a cart it couldn't check.

### Negative

- Two customers can check out the last unit of the same variant; nothing counts units.
- The customer can't be told how many are left.
- Stock is checked once per checkout session. Resuming a live session runs no new check
  (PIN-330), so a variant that sells out while the session is live isn't caught.
- The cart service doesn't check stock on add, so a client that skips the app's check can
  add an out-of-stock variant. Checkout catches it later.
- Stock is changed only by SQL against the product database; there is no admin path.

### Mitigations

- Quantities and reservations belong to the inventory epic (PIN-56). Building them means
  replacing the flag with stock records and moving the checkout check to a reservation,
  which needs its own ADR.
- A cart that sits outside checkout is checked again when its owner next starts one, so
  stock that ran out while it sat is caught then.
