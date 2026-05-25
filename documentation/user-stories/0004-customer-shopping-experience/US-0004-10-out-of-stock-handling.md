# US-0004-10: Out of Stock Handling

## User Story

**As a** customer who wants to purchase a product that is currently out of stock,
**I want** to see a clear out-of-stock indication and be offered helpful alternatives,
**So that** I understand I cannot add the item now and have options for when it becomes available.

## Story Details

| Field | Value |
|-------|-------|
| Story ID | US-0004-10 |
| Epic | [US-0004: Customer Shopping Experience](./README.md) |
| Priority | Must Have |
| Phase | Phase 3 (Cart Foundation) |
| Story Points | 5 |

## Description

This story implements the out-of-stock experience across the product detail page and the cart. When the Inventory Service reports that a variant is `OUT_OF_STOCK`, the Add to Cart button is disabled, an "Out of Stock" indicator is shown, and a "Notify When Available" option is offered. If a product becomes out of stock while already in a customer's cart, a warning is shown on the cart page. Alternative products are suggested to help the customer find a substitute.

## UI Requirements

- "Out of Stock" badge/label on the availability indicator (red/destructive variant)
- Add to Cart button disabled with `aria-disabled="true"`
- "Notify When Available" button or link visible for out-of-stock items
- Alternative products section (reuses related products or displays search suggestions)
- Cart page warning banner when a cart item becomes out of stock
- In search results: "Out of Stock" badge on product cards where applicable

## Sequence Diagram

```mermaid
sequenceDiagram
    participant CU as Customer
    participant WA as Web Application
    participant INV as Inventory Service
    participant SC as Shopping Cart Service

    CU->>WA: Click "Add to Cart"
    WA->>INV: GET /api/v1/inventory/availability/{variantId}
    INV-->>WA: {status: "OUT_OF_STOCK", available: 0}

    WA-->>CU: Show "Out of Stock" message
    WA-->>CU: Display "Notify When Available" option
    WA-->>CU: Show alternative products
    Note over WA,CU: Add to Cart button remains disabled; no cart request made

    CU->>WA: Click "Notify When Available"
    WA-->>CU: Show email capture or confirmation (if already signed in)
```

## Acceptance Criteria

### AC-0004-10-01: Out of Stock Message Displayed (from AC-E2.1)

**Given** the Inventory Service reports a variant as `OUT_OF_STOCK`
**When** I view the product detail page for that variant
**Then** I see a clearly visible "Out of Stock" label in a negative/red color
**And** the label is accompanied by a text label (not color alone)

### AC-0004-10-02: Add to Cart Button Disabled (from AC-E2.4)

**Given** the selected variant is out of stock
**When** I view the product detail page
**Then** the Add to Cart button is visually disabled
**And** clicking it has no effect (no API call is made)
**And** the button has `aria-disabled="true"` for accessibility

### AC-0004-10-03: Notify When Available Option (from AC-E2.2)

**Given** the selected variant is out of stock
**When** I view the product detail page
**Then** a "Notify When Available" button or link is displayed near the out-of-stock indicator

**Given** I click "Notify When Available" and I am a guest
**When** the dialog opens
**Then** I am prompted to enter my email address to be notified

**Given** I click "Notify When Available" and I am signed in
**When** the action is submitted
**Then** my account email is used automatically and I see a confirmation message

### AC-0004-10-04: Alternative Products Suggested (from AC-E2.3)

**Given** the selected variant is out of stock
**When** I view the product detail page
**Then** an "Alternatives you might like" or related products section is displayed
**And** the suggested products are in-stock alternatives
**And** each suggestion shows the product name, image, and current price

### AC-0004-10-05: Out of Stock in Cart Warning

**Given** I have an item in my cart
**When** that item becomes out of stock (between visits or after a restock depletion)
**Then** on the cart page, a warning banner is shown: "[Product Name] is now out of stock and cannot be included in your order"
**And** the item is visually distinguished (e.g., grayed out) on the cart page
**And** a "Remove" button is prominently shown for the out-of-stock item

### AC-0004-10-06: Out of Stock Badge on Search Results

**Given** a product in search results has `availability: "OUT_OF_STOCK"`
**When** I view the search results
**Then** an "Out of Stock" badge is displayed on the product card
**And** the card is not removed from the results

### AC-0004-10-07: Variant-Level Out of Stock

**Given** a product has multiple variants (Black and White)
**And** the White variant is out of stock but Black is in stock
**When** I view the product detail page
**Then** the Black variant shows as "In Stock" with the Add to Cart button enabled
**And** selecting the White variant shows "Out of Stock" and disables Add to Cart
**And** the Black variant is not affected

### AC-0004-10-08: No Cart Operation on Out of Stock Add Attempt

**Given** a variant is out of stock
**When** I attempt to add it to the cart (via any means)
**Then** no `POST /api/v1/carts/items` request is made
**And** the Shopping Cart Service is not called
**And** an appropriate error is displayed to the customer

## Technical Implementation

### Frontend Stack

- **Framework**: TanStack Start with React 19.2
- **UI Components**: shadcn/ui Badge, Dialog for notification signup
- **Styling**: Tailwind CSS 4
- **Notification signup**: `POST /api/v1/notifications/back-in-stock`

### Component Structure

```
frontend-apps/customer/src/
├── components/
│   └── product/
│       ├── OutOfStockIndicator.tsx
│       ├── NotifyWhenAvailable.tsx
│       └── OutOfStockAlternatives.tsx
└── components/
    └── cart/
        └── CartItemOutOfStockWarning.tsx
```

### Out of Stock Guard

```typescript
function AddToCartButton({ availability, onAdd }: Props) {
  const isOutOfStock = availability?.status === 'OUT_OF_STOCK';

  return (
    <Button
      onClick={isOutOfStock ? undefined : onAdd}
      disabled={isOutOfStock}
      aria-disabled={isOutOfStock}
    >
      {isOutOfStock ? 'Out of Stock' : 'Add to Cart'}
    </Button>
  );
}
```

### Backend: Notification Service

When a customer opts in for back-in-stock notification:

```
POST /api/v1/notifications/back-in-stock
Content-Type: application/json

{
  "variantId": "...",
  "email": "customer@example.com"
}
```

The Notification Service listens for inventory restock events and sends notifications to subscribers.

## Accessibility Requirements

- "Out of Stock" is conveyed via text, not color alone
- Disabled button uses both `disabled` attribute and `aria-disabled="true"`
- "Notify When Available" dialog is a proper modal with focus trap
- Cart warning uses `role="alert"` so it is announced immediately

## Definition of Done

- [ ] "Out of Stock" badge displayed on PDP when variant is out of stock
- [ ] Add to Cart button disabled; no cart API call made
- [ ] "Notify When Available" button visible for out-of-stock items
- [ ] Notification signup works for guest (email input) and authenticated users
- [ ] Alternative products section displayed for out-of-stock items
- [ ] Out-of-stock badge appears on search result cards
- [ ] Cart page shows warning for items that became out of stock
- [ ] Variant-level out-of-stock handled independently per variant
- [ ] Accessibility: disabled button has `aria-disabled="true"`, "Out of Stock" is text-labeled
- [ ] Unit tests cover out-of-stock guard logic
- [ ] Code reviewed and approved

## Dependencies

- [US-0004-04: Product Detail Page](./US-0004-04-product-detail-page.md)
- [US-0004-05: Product Variant Selection](./US-0004-05-product-variant-selection.md)
- [US-0004-06: Add Item to Cart](./US-0004-06-add-item-to-cart.md)
- Notification Service back-in-stock signup endpoint

## Related Documents

- [Journey Error Scenario E2: Product Out of Stock During Add to Cart](../../journeys/0004-customer-shopping-experience.md#e2-product-out-of-stock-during-add-to-cart)
- [US-0004-06: Add Item to Cart](./US-0004-06-add-item-to-cart.md)
