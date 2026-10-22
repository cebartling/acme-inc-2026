# ADR-0045: Cart Calls the Product Service Synchronously and Fails Closed

## Status

Accepted

## Context

The shopping cart service (`backend-services/shopping-cart`) holds no catalog data of its
own. Two of its decisions need the product service's current answer:

- **Price.** A request never carries a price. Add (`AddItemToCartUseCase`), quantity update
  (`UpdateCartItemQuantityUseCase`) and merge on sign-in (`MergeCartsUseCase`) read
  `GET /api/v1/prices/{variantId}` through `ProductPricingClient`. The line is priced at its
  new total quantity, so crossing a tier threshold reprices the whole line.
- **Stock at checkout.** Starting checkout (`StartCheckoutUseCase`, PIN-329) reads
  `GET /api/v1/inventory/availability/{variantId}` through `ProductAvailabilityClient`, once
  per distinct variant. The product service only knows in or out of stock, so there are no
  quantities and no reservation (PIN-56).

When the product service can't answer, the cart has to choose between guessing (the last
price it stored, or assuming the variant is in stock) and refusing the request.

## Decision

The cart calls the product service synchronously, in the request, and refuses rather than
guesses when it gets no trustworthy answer.

1. **One blocking `RestClient`.** `ProductClientConfig` builds `productRestClient` on a
   `SimpleClientHttpRequestFactory` with a 2-second connect timeout and a 2-second read
   timeout, so a slow product service can't hold an Add to Cart click open. The base URL is
   `acme.product.base-url` (`PRODUCT_SERVICE_URL`). There is no circuit breaker, no retry and
   no cache.
2. **Fail closed with 503.** A timeout, network error, unexpected status or empty body
   becomes `CartError.PricingUnavailable` (503 `PRICING_UNAVAILABLE`) or
   `CartError.AvailabilityUnavailable` (503 `AVAILABILITY_UNAVAILABLE`) in
   `CartController.statusFor`. An availability value other than `IN_STOCK` or `OUT_OF_STOCK`
   is treated the same way. Checkout is refused rather than skipping the check.
3. **Only the product service's own `VARIANT_NOT_FOUND` 404 means "gone".** Both clients read
   the error body's `code`; a 404 from a gateway, a wrong URL or a non-JSON body counts as
   unavailable (PIN-306). A gone variant is a 404 `VARIANT_NOT_FOUND` on add and update, is
   left out of a merge and listed in `mergeResult.itemsUnavailable`, and is `NOT_AVAILABLE`
   in checkout's `validationErrors`.
4. **Calls run outside the transaction.** Pricing and availability are fetched before the
   cart's transaction opens. Add and update retry only the transaction on a version conflict
   (the price does not depend on the cart); merge and checkout rerun the lookups too
   ([ADR-0048](0048-cart-optimistic-locking.md)).

This differs from [ADR-0024](0024-synchronous-customer-lookup-for-notifications.md), where
notification's `CustomerServiceClient` calls customer behind a Resilience4j circuit breaker
(`ResilienceConfig`). That call runs in a Kafka consumer that can retry later; the cart's
calls answer a customer who is waiting, so the cart fails fast instead.

## Consequences

### Positive

- The cart never stores a price the product service didn't give it, and never locks a cart
  for checkout on an assumed stock answer.
- A customer gets a fast, explicit 503 instead of a hung request or a wrong total.
- Archived products (PIN-306) don't block sign-in: their lines drop out of the merge and are
  named in the notice, while a real outage still fails it.

### Negative

- Add, update, merge and checkout are unavailable whenever the product service is. Viewing,
  removing and clearing still work, since they make no product call.
- The calls are sequential. A merge prices each distinct guest variant in turn and a
  checkout checks each in turn, so a slow product service can cost up to 4 seconds (connect
  plus read) per variant before the request fails.
- With no breaker, every request during an outage still waits out its timeouts, and the
  product service gets no relief while it recovers.
- An add or update to a cart locked for checkout calls pricing before `Cart` refuses it with
  409 `CART_LOCKED`, so during a product outage it answers 503 instead.
- The customer app checks availability itself before adding, and on the `/cart` page with
  its own caching and timeouts (PIN-350, PIN-352, PIN-349). The server-side checkout check
  shares none of that.

### Mitigations

- The 2-second timeouts bound how long one lookup can hold a request.
- Merge stops at the first pricing failure rather than waiting on the remaining lookups.
- A circuit breaker like ADR-0024's, or a short-lived price cache, can be added to
  `ProductClientConfig` if product outages become a real problem; it would need its own ADR.
