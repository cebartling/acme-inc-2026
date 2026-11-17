# ADR-0068: Notification Publishes Through the Outbox, and Its Deliveries Read Model Follows ADR-0065

## Status

Accepted

Part of [PIN-534](https://linear.app/pintail-consulting/issue/PIN-534/wire-debezium-cdc-into-cqrs-for-all-services),
built for notification in PIN-549. Applies [ADR-0065](0065-customer-read-model-from-snapshots.md) and
[ADR-0067](0067-identity-outbox-and-users-read-model.md) to notification. Implements
[ADR-0004](0004-change-data-capture.md), [ADR-0006](0006-mongodb-query-store.md) and
[ADR-0022](0022-transactional-outbox-pattern.md) for notification.

## Context

Notification sent `NotificationSent` to Kafka with `KafkaTemplate`, from inside the use case's
transaction, and logged a failed send. That is a dual write: a delivery could commit with no event,
or an event could go out for a delivery whose transaction then rolled back. Its three lookup
endpoints (`/{id}/status`, `/recipient/{recipientId}`, `/by-email/{email}`) read PostgreSQL.

Notification differs from the services before it in three ways:

- **It is mostly a consumer.** It reacts to `identity.user.events` and `customer.events`, which
  are not changing. Only the event it publishes and the way it reads change.
- **Its event describes only a success.** `NotificationSent` is emitted when the provider accepts
  an email. A delivery that failed has no event, so events alone cannot rebuild the lookups'
  responses.
- **Its use cases swallow exceptions.** Each one turned any exception into a `Failure` result, so a
  failed outbox write would have been hidden from the handler.

## Decision

1. **Notification writes `NotificationSent` to an `outbox` table, and Debezium publishes it.** V2
   creates the table (identity's shape). `NotificationEventPublisher.publish` hands the event to
   `OutboxWriter`, which saves it in the use case's transaction. The connector `notification-outbox`
   (`docker/debezium/connectors/notification-outbox.json`) routes each row to `notification.events`,
   keyed by notification id. The topic, key, payload and event ID are unchanged. No notification
   code sends to Kafka directly, and `KafkaProducerConfig` and `spring.kafka.producer` are gone.
2. **A failed outbox write fails the handler.** `NotificationEventPublisher` wraps it in
   `OutboxWriteException`, and the three send use cases rethrow that one while still turning other
   exceptions into `Failure`. The handler then fails, its transaction rolls back (including the
   `processed_events` row), and Kafka redelivers the event. An email already sent in that attempt
   can be sent again on the retry. That window existed before, for any failure after the send.
3. **The read model is built from snapshots, as in ADR-0065.** After a delivery's final state is
   saved (SENT or FAILED), the use case also writes a snapshot of it to the outbox for the topic
   `notification.read-model`. A SENT snapshot follows the `NotificationSent` event's id; a FAILED one
   has no event, so it gets an id of its own. The snapshot is the lookup endpoints' response type
   (`NotificationStatusResponse`), so a read is deserialization with no mapping. It holds the
   recipient's email, as the endpoints always did.
4. **A projector replaces the document.** `DeliverySnapshotProjector` consumes `notification.read-model`
   and replaces the delivery's document in the `notification_delivery_read_models` collection of the
   shared `acme_query` database, keeping the last 50 source event ids. Redelivery is harmless because
   the last snapshot wins. It uses its own container factory (`ReadModelKafkaConfig`: manual
   acknowledgment, retries at 1, 2, 4, 8 and 16 seconds), because the email consumers' factory
   waits five minutes between retries. Connection settings are `MONGODB_URI` and `MONGODB_DB`, as in customer and identity.
5. **The three lookups read MongoDB.** `NotificationReadModelQueries` indexes `recipientId` and
   `recipientEmail` and returns the lists oldest first. Responses keep their shape. They are
   eventually consistent, with no `X-Read-After` wait: nothing a client calls returns an event id to
   wait for. The acceptance steps that look a notification up already poll until it appears.
6. **A backfill writes snapshots for existing deliveries.** `DeliveryReadModelBackfill` runs at
   startup when `NOTIFICATION_READ_MODEL_BACKFILL` is true, because the connector starts with
   `snapshot.mode: no_data`. Deliveries never change after they are sent, so there is no write for
   it to race with.

## Consequences

- No notification code sends to Kafka. A delivery, its event and its snapshot commit or roll back together.
- A delivery shows in the lookups a few seconds after it is sent, not at once. A client that looks it up
  straight after causing it must poll.
- **After a Kafka outage the chain recovers in order.** The outbox rows wait in PostgreSQL, Debezium
  publishes them when Kafka is back, and the projector applies the snapshots.
- A snapshot that fails five times is skipped, and that delivery stays out of date or missing until
  the backfill rewrites it. Deliveries do not change again, so there is no later snapshot to repair it.
- Every event is written to the outbox as well as to PostgreSQL's delivery table, and the outbox has no
  retention job yet, so rows accumulate until one exists (ADR-0022, PIN-395).
- `markAsDelivered` and `markAsBounced` have no callers today. A caller added later must write a
  snapshot after saving, or the read model keeps the SENT state.
- `acme_notifications` already has `dbz_publication` from `docker/postgres/init`, so the connector needs
  no extra setup in the local stack.
