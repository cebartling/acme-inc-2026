# Linear Summary: Acme Inc. 2026 (2026-09-26)

A snapshot of the **Acme Inc. 2026** Linear project: every issue still open, grouped by epic, followed by options for what to work on next. It was taken on 2026-09-26, after PIN-278 (optimistic locking on the cart) was merged in PR #141. It was updated twice later the same day: after PIN-296 (open-in-view off in the cart service) was merged in PR #142, and after PIN-294 (clear the entire cart) was merged in PR #143.

The project has **97 issues: 76 Done and 21 in Backlog**. Nothing is In Progress or Todo.

## Open issues by epic

### Epic 009: Shopping Cart Management (PIN-62)

| Issue | Priority | Title |
|---|---|---|
| PIN-291 | High | Cart checkout transition: validate, lock and convert the cart |
| PIN-292 | Medium | Promotions and coupon codes on the cart |
| PIN-293 | Medium | Detect abandoned carts and recover them |
| PIN-279 | Low | Cart: decimal quantity (e.g. 2.9) is silently truncated instead of rejected |
| PIN-295 | Low | Estimated tax on the cart |

PIN-273 is also linked to this epic as related; its parent is Epic 003.

Completed since the first snapshot:
- **PIN-296** (shopping-cart: turn off open-in-view like the other services), merged in PR #142 (`fdcdb8a`).
- **PIN-294** (clear the entire cart), merged in PR #143 (`34de2e6`). It added `DELETE /api/v1/carts/{cartId}/items`, the `CartCleared` event, and a "Clear cart" action with confirmation on `/cart`.

### Epic 003: Product Inventory Management (PIN-56)

| Issue | Priority | Title |
|---|---|---|
| PIN-273 | High | US-0004-10: Out of Stock Handling (also related to PIN-62) |
| PIN-275 | Medium | US-0004-13: Inventory Service Resilience |

### Epic 011: Pricing Management (PIN-64)

| Issue | Priority | Title |
|---|---|---|
| PIN-274 | Medium | US-0004-11: Price Change Handling |

### No parent epic

| Issue | Priority | Title |
|---|---|---|
| PIN-270 | Medium | AC-0004-09-08: expose search circuit breaker metrics via Prometheus |

### The epics themselves

| Issue | Priority | Epic |
|---|---|---|
| PIN-61 | Urgent | Epic 008: Payment Management |
| PIN-58 | High | Epic 005: Order Management |
| PIN-60 | High | Epic 007: Shipping & Fulfillment Management |
| PIN-64 | High | Epic 011: Pricing Management |
| PIN-53 | None | Epic 000: Identity Management |
| PIN-54 | None | Epic 001: Observability Platform |
| PIN-55 | None | Epic 002: Product Catalog |
| PIN-56 | None | Epic 003: Product Inventory Management |
| PIN-57 | None | Epic 004: Customer Management |
| PIN-59 | None | Epic 006: Notifications Management |
| PIN-62 | None | Epic 009: Shopping Cart Management |
| PIN-63 | None | Epic 010: Analytics & Business Intelligence |

Apart from the cart epic, none of these has open stories filed under it.

## Options for what to do next

1. **Close out the last small cart item: PIN-279** (recommended). It's a one-setting validation fix, and the cart code is fresh after this week's work (PIN-268, PIN-287, PIN-288, PIN-289, PIN-278, PIN-296 and PIN-294).
   - PIN-296 was the first item on this list, and it's done: the merge's "already merged" check now works without relying on the version check.
   - PIN-294 was the second, and it's done too.

   Epic 009 would then be down to its three large features: checkout, promotions and abandonment.

2. **Prepare for checkout: PIN-273, then PIN-291.**
   - Checkout has to confirm every item is available, and PIN-273 is that groundwork. It also holds PIN-266's deferred "Only N available" criterion.
   - PIN-291 then needs splitting into stories. Journey `documentation/journeys/0005-customer-shopping-cart-checkout-experience.md` describes the flow (step 1, `POST /api/v1/checkout/initiate`, and error scenarios E1, E5 and E6).
   - Checkout will immediately depend on the Order (PIN-58) and Payment (PIN-61) epics, which have no stories yet. This is the path to an end-to-end purchase, but it's a multi-week effort.

3. **Break down Payment or Order first.** Payment (PIN-61) is the only epic marked Urgent, yet it has no stories. Writing its user stories, as the US-0004 stories were written for the cart, would be planning work rather than code, and it would unblock option 2.

4. **Smaller standalone items: PIN-275, PIN-270, PIN-274.**
   - PIN-275 (inventory resilience) is self-contained.
   - PIN-270 is a small metrics task with no parent epic; Observability (PIN-54) looks like the natural home.
   - PIN-274 (price-change handling) is the other cart-adjacent story.

## Related issues outside the project

Several open issues concern this repository but have no project set, so they aren't counted above:
- PIN-253 (High, In Progress): CI only covers the customer frontend.
- PIN-254 (High): the email verification token is broadcast on the shared `identity.user.events` topic.
- PIN-252 (Medium): major dependency upgrades.
- PIN-255 (Medium): the docs and infra describe Avro/Schema Registry, which the code never implemented.
- PIN-258 (Medium): the acceptance tests' typecheck and lint are red.

Setting their project to Acme Inc. 2026 would bring them into this view.
