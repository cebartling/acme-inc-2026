# ADR-0066: The Cart Read Model Follows ADR-0065, and the Order Service Hands the Browser the Cart's Read-After

## Status

Accepted

Part of [PIN-534](https://linear.app/pintail-consulting/issue/PIN-534/wire-debezium-cdc-into-cqrs-for-all-services),
built for shopping-cart in PIN-551. Applies [ADR-0065](0065-customer-read-model-from-snapshots.md)
to the cart. Implements [ADR-0004](0004-change-data-capture.md) and
[ADR-0006](0006-mongodb-query-store.md) for the cart.

## Context

The cart already had a transactional outbox ([ADR-0022](0022-transactional-outbox-pattern.md),
PIN-384): every use case, and the expiry, purge and checkout-unlock jobs, wrote its event to the
`outbox` table in the cart's own transaction, and a polling `OutboxRelay` sent the rows to Kafka.
Nothing else sent to Kafka. What was missing was the rest of the PIN-534 pattern: Debezium
publishing the outbox, and `GET /api/v1/carts/current` reading from MongoDB.

The cart is the screen most sensitive to stale reads. The customer app reads it again after most
changes, and the header badge reads it on every page. A shopper who has just added an item and
sees the cart without it has seen a bug.

Three things differ from the customer:

- **Nothing in the cart's state is PII**, but the events don't carry the state either. A
  `CartItemRemoved` names the line, not the cart that is left. Enriching `cart.events` would
  change payloads, which PIN-551 forbids.
- **Some cart changes have no event of their own.** `CartMerged` is written for the target cart.
  The guest cart it absorbs becomes `MERGED` without an event.
- **Some cart changes are made by another service.** The order service converts the cart when an
  order is placed. The browser sees that response, not the cart's.

## Decision

1. **Debezium publishes the cart outbox.** The connector `cart-outbox`
   (`docker/debezium/connectors/cart-outbox.json`) is the customer's with the cart's database,
   slot and prefix. `cart.events` carries the same keys, payloads and event IDs as before.
   `shopping-cart-db-init` creates the `dbz_publication`, because `acme_carts` is not in
   `docker/postgres/init`, and the connector-init container waits for it. The polling relay, the
   `KafkaTemplate` producer config and the outbox's `published_at`, `retry_count` and `last_error`
   columns are gone (`V11`).
2. **A second outbox row carries a snapshot**, as for the customer. `OutboxWriter.write(event)`
   also writes the cart's full state, built after the change from the response type
   (`CartResponse`), to `cart.read-model`, keyed by cart ID, with `sourceEventId`. A purged cart's
   snapshot carries no cart.
3. **A cart changed without an event gets a snapshot of its own.** `OutboxWriter.writeSnapshot(cartId)`
   writes one with no event and leaves the `X-Read-After` header alone. The merge calls it for the
   guest cart, after the change, because the snapshot written when that cart left checkout still
   shows it `ACTIVE`.
4. **The projector replaces the document.** `CartSnapshotProjector` upserts `cart_read_models`
   (database `acme_query`) by cart ID with the owner (`userId` or `sessionId`), the status, the
   response body as JSON text (`cartJson`, served as it is) and the last
   50 `sourceEventId`s. A snapshot with no cart deletes the document.
5. **Reads wait for their own writes.** A change's response carries `X-Read-After: <event id>`. A
   `GET /carts/current` that sends it back waits (polling, 5 s) until any document has applied that
   event, then reads the owner's `ACTIVE` or `CHECKOUT` cart. If the event does not arrive it is a
   `503` with `Retry-After: 1`, never a stale cart or a false 204. A header that is not a UUID is a
   `400`. The wait is on any document, not the owner's current cart, because the change may have
   ended that cart. The endpoint needs no sign-in, so at most 50 reads wait at once
   (`acme.cart.read-model.max-concurrent-waits`); with every slot taken, a read for an event not
   yet applied gets the `503` straight away instead of holding a request thread.
6. **The customer app sends the id for 30 seconds, not once.** Every cart-service response's
   `X-Read-After` is remembered, and every cart read in the next 30 seconds sends it. Customer's
   client sends it once, but the cart is read from several places at once (the badge, a focus
   refetch, the refetch after a merge). The window is bounded because a document keeps only its
   last 50 events, so an old id would never be found.
7. **The order service passes on the cart's id for the conversion.** The cart answers the order
   service's convert call with `X-Read-After`. `CartClient` reads it, `PlaceOrderResult.Placed`
   carries it, and `POST /api/v1/orders` returns it as `X-Cart-Read-After`, exposed through CORS.
   `orderApi.place` feeds it to the same 30-second window. It is a different name from
   `X-Read-After` because the order service has no read model: a header with that name on an order
   response would look like an id to send back to the order service.
8. **The consumer is configured in code.** The cart takes `spring-kafka` without Boot's Kafka
   auto-configuration, like the customer, so `KafkaConsumerConfig` declares the consumer with
   `@EnableKafka` and manual acknowledgement. Without it the `@KafkaListener` never starts and no
   consumer group exists. A test sends a snapshot through real Kafka to MongoDB, and fails without
   `@EnableKafka`.

## Consequences

- **A change made while Kafka is down is kept** in the outbox and reaches the read model after
  Kafka and the connector are back. Reads that ask for it answer `503` until then. The `@kafka-outage`
  scenario covers it.
- **Debezium drops empty arrays**, so an emptied cart's snapshot arrives without `items`.
  `CartResponse.items` defaults to empty, and a projector test covers it. Any new list field in
  `CartResponse` needs a default too.
- **Existing carts have no document.** The connector uses `snapshot.mode: no_data`.
  `CartReadModelBackfill` writes a snapshot for each `ACTIVE` and `CHECKOUT` cart when the service
  starts with `ACME_CART_READ_MODEL_BACKFILL_ON_STARTUP=true`. Until then those shoppers read
  `204`, as if they had no cart. A cart changed between the backfill reading its state and writing
  its row is overwritten with the older state until it changes again, so run it before traffic or
  when it is quiet.
- **Redelivery can briefly serve older state.** The projector replaces the document without
  comparing versions. After a connector or consumer restart that replays older snapshots, the
  document goes back in time until the replay reaches the newest one, though the newer event id
  stays in `appliedEventIds` and a waiting read returns at once. A version in the snapshot, with a
  conditional upsert, would close the gap; it is not worth the change for a local demo.
- **A snapshot that fails five times is skipped**, and the cart stays out of date until its next
  change, which writes a full snapshot.
- **Snapshots of two carts are ordered only within a partition.** A merge writes the target's
  snapshot and the guest cart's, with different keys. Locally the topic has one partition, so they
  apply in commit order. With more, the old guest session could read the merged cart for a moment
  after the signed-in customer sees the merge. The same goes for a conversion followed by a new
  cart: both documents can look current for a moment. A read that waited for an event prefers the
  current document that applied it; one that sent nothing may get either.
- **A client that is not the customer app** gets an eventually consistent read unless it sends the
  header. The acceptance tests' `ApiClient` does for any service.
- **The order service now depends on the cart's header.** If the cart stopped sending it,
  `X-Cart-Read-After` would be absent and the customer app would read the cart without waiting,
  as before. The acceptance step asserts the header is there.
- **Stop old instances before `V11` runs.** An old instance would fail to insert outbox rows once
  the columns are gone. The outbox keeps every row until a purge job exists (PIN-395).
- **Drain the old relay before `V11` runs.** The connector publishes only rows written after it
  starts, and `published_at` is the only record of which rows the relay never sent. `V11` fails
  while any row has `published_at IS NULL`, so none is dropped unsent.
- **MongoDB is configured by host, port and database**, like the customer. PIN-538 moves both to
  a connection string with credentials and TLS, for DocumentDB.
