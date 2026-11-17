# ADR-0067: Identity Publishes Through the Outbox, and Its Users Read Model Follows ADR-0065

## Status

Accepted

Part of [PIN-534](https://linear.app/pintail-consulting/issue/PIN-534/wire-debezium-cdc-into-cqrs-for-all-services),
built for identity in PIN-548. Applies [ADR-0065](0065-customer-read-model-from-snapshots.md) to
identity. Implements [ADR-0004](0004-change-data-capture.md), [ADR-0006](0006-mongodb-query-store.md)
and [ADR-0022](0022-transactional-outbox-pattern.md) for identity.

## Context

Identity sent 19 event types over 6 topics straight to Kafka with `KafkaTemplate`
([ADR-0042](0042-best-effort-event-publishing.md)). Each send was a dual write: the use case saved
its state and appended the event to the `event_store` table, then sent the event, and a crash
between the two left Kafka and the database disagreeing. Customer and notification consume
`identity.user.events`, so a lost registration or activation event broke the flows after it.

Three things differ from customer, cart and order:

- **Some events describe Redis, not PostgreSQL.** Sessions, device trusts and token-reuse
  detection live in Redis. Their events are written outside any database transaction.
- **A user's state is mostly secret.** The `User` row holds the password hash, the TOTP secret, a
  phone number and the last device fingerprint. A read model that copied it would copy those.
- **Most events do not change what a read model would hold.** Session and device events are keyed
  by session or device, not user, and a sign-in changes nothing a list of users shows.

## Decision

1. **Identity writes its events to an `outbox` table, and Debezium publishes it.** V13 creates the
   table (customer's and order's shape, without the columns only a polling relay needs).
   `OutboxWriter` saves each event in the caller's transaction. The connector `identity-outbox`
   routes rows to the topic in each row, keyed by aggregate id. Topics, keys, payloads and event
   IDs are unchanged, so customer's and notification's consumers keep working and notification's
   deduplication by event ID still holds. `UserEventPublisher` keeps its 19 methods and their callers,
   and hands each event to the writer. Identity never had a polling relay, so there is no cutover
   to drain.
2. **A failed outbox write fails the request.** The old sends were best-effort: a failure was
   logged and the request went on. Now an event is never silently dropped, and a database outage
   that stops the outbox write also stops the change it describes. Customer, cart and order made the same choice.
3. **Redis-backed events get their own transaction.** `OutboxWriter.write` joins the caller's
   transaction when there is one and opens its own when there is not. The event is saved, but not
   atomically with the Redis change: a crash between them can leave a session that has no
   `SessionCreated`, or an event for a session that was never stored.
4. **The read model is built from snapshots, as in ADR-0065.** `UserRegistered`, `EmailVerified`,
   `UserActivated`, `AccountLocked` and `AccountUnlocked` also write a snapshot of the user to the
   outbox, for the topic `identity.read-model`. The snapshot holds the id, email, names, status,
   email verification, lock time, registration source and timestamps. It **leaves out** the
   password hash, TOTP secret, phone number and device fingerprint, so those never reach Kafka or MongoDB.
5. **A projector replaces the document.** `UserSnapshotProjector` consumes `identity.read-model`
   and replaces the user's document in the `user_read_models` collection of the shared
   `acme_query` database, keeping the last 50 source event ids. Redelivery is harmless because the
   last snapshot wins. `UserReadModelBackfill` writes a snapshot for every existing user when
   `IDENTITY_READ_MODEL_BACKFILL` is true, because the connector starts with `snapshot.mode: no_data`.
6. **Nothing reads the read model yet.** Identity's reads stay where they are, as in
   [ADR-0006](0006-mongodb-query-store.md). The test API (`test`/`docker` profiles only) reads a user's
   document back for acceptance tests, and writes a snapshot when it sets a status directly.

## Consequences

- No identity code sends to Kafka. A change and its event commit or roll back together, except for
  the Redis-backed events in decision 3.
- Every event is written twice: to `event_store` (the audit history) and to the outbox. The outbox
  has no retention job yet, so rows accumulate until one exists (ADR-0022, PIN-395).
- An expired lock has no event. A locked user stays `LOCKED` in the read model, with a `lockedUntil`
  in the past, until their next sign-in unlocks them. (A password reset that unlocks a locked user
  publishes `AccountUnlocked`, so it updates the read model at once.) Readers must compare `lockedUntil` with the
  clock rather than trust the status alone.
- A snapshot is built after the change is saved, in the same transaction. A use case that wrote
  the event before saving the user would snapshot the old state, so the five snapshot-writing
  methods must be called after the save. Today's callers all do.
- Debezium's event router re-writes JSON numbers as doubles. Identity's payloads carry integers,
  strings and timestamps, so nothing changes for them.
- The `identity_events_publication` that V3 created for `event_store` is not used; the connector
  reads `dbz_publication`, which `docker/postgres/init` creates.
- Changes made behind the application's back, such as the test API's, need a snapshot written by
  hand, as `TestController.setUserStatus` does.
