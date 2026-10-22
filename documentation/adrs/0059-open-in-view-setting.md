# ADR-0059: JPA open-in-view Off Except in Product

## Status

Accepted

Related to [ADR-0048](0048-cart-optimistic-locking.md), whose fresh re-read depends on it.

## Context

Spring Boot turns `spring.jpa.open-in-view` on unless a service sets it. With it on, one
JPA session spans the whole web request, so an entity can lazy-load its associations after
the repository call and the transaction have finished.

The shopping cart ran with the default until PIN-296 (`fdcdb8a`). Because one session
spanned the request, a read inside a transaction returned the copy the request had already
loaded. The merge's locked re-read could not see a merge committed in between, so conflicts
and 409s were likelier than they needed to be. Nothing in the cart needed the open session:
every lookup used outside a transaction already fetched the cart's lines with an entity
graph.

## Decision

Each service sets `spring.jpa.open-in-view: false` in its `application.yml`, except
product.

| Service | Setting | Source |
|---------|---------|--------|
| Customer, identity, notification | `false` | `application.yml` |
| Shopping cart | `false` | `application.yml`, PIN-296 |
| Order | `false` | `application.yml`, "Like the other services (PIN-296)" |
| Product | unset, so on | Boot's default |

Product keeps it on because its product detail, price and availability controllers read
lazy associations (variants, images, tier pricing, a variant's product) after the
repository call returns. Turning it off means loading those inside a transaction first
(`ARCHITECTURE.md`, Product Service).

## Consequences

### Positive

- Where it is off, a read inside a use case's transaction is fresh, which the cart's
  optimistic-locking retry relies on.
- A lazy read outside a transaction fails rather than querying silently. The cart's
  `CartResponsesOutsideSessionTest` maps the real use cases' carts with no session open,
  so a new lazy read fails in CI.

### Negative

- Product behaves differently from the other five services. Code or habits carried over
  from product, such as reading a lazy association in a controller, break elsewhere.
- In product, a read inside a transaction can return the copy the request loaded earlier,
  the problem the cart had before PIN-296.
- The setting is per service ([ADR-0054](0054-independent-service-builds.md)), so a new
  service gets Boot's default unless it sets the property.

### Mitigations

- Turning it off in product needs those controllers to load their associations in a
  transaction or with entity graphs first, with a test like the cart's.
- A new service copies the `open-in-view: false` line, as order did.
