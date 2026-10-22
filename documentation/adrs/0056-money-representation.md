# ADR-0056: Money as NUMERIC(10,2) and BigDecimal Without Currency

## Status

Accepted

Related to [ADR-0045](0045-cart-product-calls-fail-closed.md), which covers how the cart
calls the product service for prices.

## Context

Prices exist in two services. The product service owns them: a product's price, a
variant's optional `price_override` and a variant's quantity tiers. The cart stores the
unit price of each line. The order service will copy the cart's lines into an order
(PIN-366), but has no orders table yet (`V1__baseline.sql`).

## Decision

1. **Amounts are `NUMERIC(10,2)` in PostgreSQL.** That covers `products.price`
   (`V1__create_products.sql`), `product_variants.price_override` and
   `product_variant_tier_pricing.price` (`V3__add_product_variants.sql`), and
   `cart_items.unit_price` (shopping-cart `V1__create_carts.sql`).
2. **Amounts are `java.math.BigDecimal` in Kotlin,** from the JPA entities (`Product`,
   `ProductVariant`, `ProductVariantTierPricing`, `CartItem`) through the API DTOs. JSON
   carries them as numbers. A cart line's total is `unitPrice × quantity` and the subtotal
   is the sum of line totals (`CartItem.lineTotal`, `Cart.subtotal`), with no rounding step.
3. **Stored amounts carry no currency.** No price table has a currency column. The cart
   adds one constant when it leaves the service: `AddItemToCartUseCase.CURRENCY = "USD"`,
   documented as "Product prices carry no currency; the catalog is USD-only today". It
   fills `summary.currency` in cart responses and the `Money(amount, currency)` value in
   the `ItemAddedToCart` and `CheckoutInitiated` events.
4. **Prices are set on the server.** The cart reads every unit price from the product
   service's `GET /api/v1/prices/{variantId}` and never takes one from the request
   (`ProductPricingClient`, PIN-266). The caller picks the tier for the line's quantity.

## Consequences

### Positive

- `BigDecimal` and `NUMERIC` are exact, so totals have no floating-point error.
- A customer can't set the price of their own cart line.

### Negative

- The platform has one currency, and it is implied rather than stored. Customer keeps a
  `preferredCurrency` (default `"USD"`) that no price uses. Selling in a second currency
  would need a currency column on every price and on cart lines.
- `NUMERIC(10,2)` caps an amount below 100,000,000 and fixes two decimal places, which
  doesn't fit currencies with zero or three minor-unit digits.
- `Product.price` is mapped with `precision = 12`, while its column is `NUMERIC(10,2)`.
  `ddl-auto: validate` checks the column's type, not its precision, so the mismatch passes.
- The customer app types these fields as JavaScript `number` (`services/api.ts`), so
  anything it adds up itself, such as the cart page's totals without out-of-stock lines
  (PIN-317), is floating-point.
- No backend code rounds or rescales, so amounts stay at two decimals only because every
  input comes from a two-decimal column and quantities are integers.

### Mitigations

- The `CURRENCY` constant is the one place the cart names the currency, so a second
  currency starts there and in the price tables.
- The order service's orders table (PIN-366) can carry amounts the same way, or add a
  currency column, when it is created.
