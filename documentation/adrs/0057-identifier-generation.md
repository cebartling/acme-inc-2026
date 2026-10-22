# ADR-0057: Identifier Generation

## Status

Accepted

## Context

Every table but one has a `UUID` primary key (customer's `customer_number_sequences` is
keyed by year-month), and the services make those UUIDs in three different ways:

- **UUID v7 from `java-uuid-generator`.** Identity's `UserIdGenerator` and customer's
  `CustomerIdGenerator` wrap `Generators.timeBasedEpochGenerator()`. `RegisterUserUseCase`
  uses the first for user IDs; `CreateCustomerUseCase` and `AddAddressUseCase` use the
  second for customer and address IDs.
- **Random v4 from `UUID.randomUUID()`.** The cart makes cart, line and checkout-session
  IDs this way (`Cart.kt`), with no database default. So do identity's MFA challenges and
  used TOTP codes, customer's consent records (`GrantConsentUseCase`), order's outbox
  messages, and the event IDs and fallback correlation IDs of every service that has
  events.
- **Random v4 from PostgreSQL.** Product's tables default their `id` to
  `gen_random_uuid()` (`V1__create_products.sql`, `V3__add_product_variants.sql`), and the
  seed migrations rely on it.

Two entities also have a number for people to read. A customer number is
`ACME-YYYYMM-NNNNNN` (`CustomerNumber`), counted per month by the `next_customer_number`
function over the `customer_number_sequences` table
(`V3__create_customer_number_sequences_table.sql`). The order number is
`ACME-YYYYMMDD-NNNNNN` (`OrderNumber`), counted per day by `next_order_number` over the
order service's `order_number_sequences` table (`V3__create_orders.sql`, PIN-366).

## Decision

1. **The IDs of users, customers and addresses are UUID v7,** made in application code by
   the service's ID generator before the row is saved.
2. **The order ID is a UUID v7 too,** made by the order service's `OrderIdGenerator`, and
   its order number `ACME-YYYYMMDD-NNNNNN`, as journey 0005's AC-5.4 requires (PIN-366).
3. **A number a customer reads is separate from the key.** It is a formatted,
   date-prefixed sequence, and the UUID stays the primary key and the ID in events.
4. **Other IDs stay as built.** Cart, product, event and token IDs are random v4. No
   document sets a rule for them, so this ADR records them rather than changing them.

## Consequences

### Positive

- v7 IDs sort by creation time, so the customer and user indexes take new rows at the end.
- A UUID can be made before the insert, so an event or outbox row can name it in the same
  transaction.

### Negative

- Nothing says which kind a new entity should use, and the code is split between them.
- Comments promise v7 where the code makes v4. `consent_records.id` is documented as
  "UUID v7" in `V9__create_consent_records_table.sql`, and `MfaChallenge.id` as "UUID v7,
  time-ordered", but both come from `UUID.randomUUID()`.
- Product declares `java-uuid-generator` in its `build.gradle.kts` but doesn't use it.
- Product's rows are created only by its seed migrations, and its entities take the `id`
  they are given, so product has no ID rule in code. Every other service sets its IDs in
  Kotlin.

### Mitigations

- The order service follows AC-5.4 with its own generator, as customer and identity have
  ([ADR-0054](0054-independent-service-builds.md)).
- A new aggregate's story says which kind of ID it uses.
