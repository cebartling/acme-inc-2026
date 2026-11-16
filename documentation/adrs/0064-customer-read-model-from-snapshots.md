# ADR-0064: The Customer Read Model Is Built from State Snapshots, and Reads Wait for Their Own Writes

## Status

Accepted

Part of [PIN-534](https://linear.app/pintail-consulting/issue/PIN-534/wire-debezium-cdc-into-cqrs-for-all-services),
first built for customer in PIN-547. Implements [ADR-0004](0004-change-data-capture.md) and
[ADR-0006](0006-mongodb-query-store.md) for the customer service, and replaces the in-process
projector described in [ADR-0041](0041-per-service-persistence-model.md).

## Context

Customer wrote its MongoDB read model in-process, in the request's own transaction scope, and
never read it. Every read endpoint went to PostgreSQL, behind a Redis profile cache. Preferences
and consent events were sent to Kafka directly, from inside the use case's transaction. A Kafka
outage failed the request, and a commit that failed after a successful send left a published event
for a change that never happened.

PIN-547 asked for the pattern PIN-534 sets for every service: events through the outbox,
Debezium publishing them, a Kafka-driven projector, and reads from MongoDB.

Three things did not follow from the pattern:

- **The events don't carry the state.** `ProfileUpdated` lists the changed fields. It does not
  carry their values, and no event carries addresses or consent history. A projector fed only by
  events could not build the 12 read endpoints' responses.
- **The state is PII.** Date of birth, phone, addresses, and consent records with IP address and
  user agent. `customer.events` is consumed by the notification service, which needs none of it.
- **A projector trails the write.** A client that changes something and reads it back at once
  would see the old value, or a 404 for a new customer.

## Decision

1. **Every customer event goes through the outbox, and Debezium publishes it.** Use cases call
   `OutboxWriter.write(event, topic)` in the transaction that changes state. The connector
   `customer-outbox` (`docker/debezium/connectors/customer-outbox.json`) routes each row to its
   `topic`, keyed by `message_key`, with `event_id` as the event ID. `customer.events` carries the
   same keys, payloads and event IDs as before. The polling relay and `KafkaTemplate` sends are gone.
2. **A second outbox row carries a snapshot.** `OutboxWriter.write` also writes the customer's
   full state, built after the change, to `customer.read-model`. A snapshot reuses the API
   response types, so a read is deserialization with no mapping. `sourceEventId` names the event
   the snapshot follows. The snapshot is built in the same transaction, so it always includes the
   change.
3. **The snapshot has its own topic, so PII stays off `customer.events`.** Only the projector
   consumes `customer.read-model`. The alternative, enriching the events with full state, would
   have put date of birth, phone, addresses and consent IP addresses in front of every consumer of
   `customer.events`.
4. **The projector replaces the document.** It upserts `customer_read_models` by customer ID,
   so applying a snapshot twice, or out of a replay, leaves the same document. It keeps the last
   50 `sourceEventId`s in `appliedEventIds`.
5. **Reads wait for the caller's own write.** A write response carries `X-Read-After: <event id>`.
   A read that sends the header back waits (polling, 5 s by default) until `appliedEventIds`
   contains it. If it doesn't arrive, the read answers `503` with `Retry-After: 1`, never stale data
   or a false 404. A malformed header is `400`. A read without the header is eventually consistent.
   The header is exposed to browsers through CORS.
6. **The notification service passes the event ID it is handling** as `X-Read-After` on its
   customer lookup ([ADR-0024](0024-synchronous-customer-lookup-for-notifications.md)). Its
   `CustomerActivated` event and the snapshot behind it are published a moment apart.
7. **The Redis profile cache is removed.** Profile reads come from MongoDB, so a second cache
   only adds an invalidation path. `spring-boot-starter-data-redis` and its configuration go too.
8. **A change with no event still writes a snapshot.** `OutboxWriter.writeSnapshot` does that. The
   test helper's direct writes use it, and a backfill would.

## Consequences

- **A write made while Kafka is down is kept** in the outbox, and reaches the read model after
  Kafka and the connector are back (about 4 s in the acceptance scenario). Reads that ask for that
  write answer 503 until then.
- **Debezium drops empty arrays** when it expands the payload column, so a snapshot with no
  addresses or consents arrives without those keys. `CustomerSnapshot` defaults them to empty. Any
  new list field there needs a default too.
- **Existing customers have no document.** The connector uses `snapshot.mode: no_data`, so only
  customers written after it started are projected. Customers that predate it return 404 until
  they change. A backfill that calls `writeSnapshot` for each customer is needed before this
  reaches an environment with data.
- **A snapshot that fails five times is skipped**, and the customer stays out of date until their
  next change, which writes a full snapshot.
- **`lastActivityAt` goes stale.** `/me` updates it in PostgreSQL without an event, so MongoDB
  holds the value from the last change.
- **`X-Read-After` may name a change that rolled back.** The header is set when the event is
  written, before the commit. The read then answers 503 instead of the old value, which is safe.
- **After a restart, reads that carry the header can answer 503 for a few seconds**, while the
  projector's consumer rejoins its group.
- **Clients must send the header.** The acceptance tests' `ApiClient` does. The frontends don't
  yet, and get eventual consistency until they do.
- The outbox columns `published_at`, `retry_count` and `last_error` are unused. Dropping them is a
  separate migration.
