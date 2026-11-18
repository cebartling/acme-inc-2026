# Debezium Connector Configurations

This directory contains Debezium connector configurations for Change Data Capture (CDC).

## Registration

Every `*.json` file here is registered automatically when the application stack starts. The
`debezium-connector-init` container (`docker-compose.apps.yml`) waits for Debezium Connect, then runs
`PUT /connectors/{name}/config` for each file. The call creates or updates, so restarts are idempotent and
edited configs are applied. To re-apply after an edit, run
`scripts/docker-manage.sh apps-up debezium-connector-init`. A rejected config fails the container and `apps-up`;
read the file name and Connect's error with:

```bash
docker logs acme-debezium-connector-init
```

Useful Kafka Connect REST calls:

```bash
# List connectors with their status
curl "http://localhost:8083/connectors?expand=status"

# Delete a connector (a removed file does not delete its connector)
curl -X DELETE http://localhost:8083/connectors/<name>
```

## Adding a Connector

Create a JSON file with a top-level `name` and a `config` object, adjusting:

- `name`: Unique connector name
- `database.dbname`: Target database name
- `topic.prefix`: Kafka topic prefix for this database
- `slot.name`: Unique replication slot name

## Outbox Connectors

`customer-outbox.json`, `cart-outbox.json`, `order-outbox.json`, `identity-outbox.json` and `notification-outbox.json` route a service's `outbox` table to the topic named in each row's
`topic` column, using Debezium's `EventRouter`. The cart's `dbz_publication` is created by `shopping-cart-db-init`,
because `acme_carts` is not in `docker/postgres/init`; the connector does not create publications. Consumers get the row's `message_key` as the key and its `payload` as a
plain JSON value, and the row's `event_id` in the `id` header.

`snapshot.mode` is `no_data`, so only rows written after the connector starts are published. The default
(`initial`) would re-publish every row already in the table. The cost: if the replication slot is lost, the task
fails instead of skipping rows silently. To recover, stop the connector, `DELETE /connectors/<name>/offsets`,
drop and recreate the slot, then resume; rows written in the gap are not published.

A replication slot keeps the write-ahead log (WAL) from its confirmed position onward. The outbox connectors have no
`heartbeat.interval.ms`, so a connector on a quiet database (such as `order-outbox`) only advances its slot when one of
its own tables changes, and can hold back WAL that the busier databases on the same Postgres produce. Watch slot lag
(`SELECT slot_name, pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn)) FROM pg_replication_slots`).
If it grows on a quiet database, add `heartbeat.interval.ms` to the connectors.

## Table Capture Connectors

`product-catalog.json` captures product's four catalog tables (`products`, `product_variants`,
`product_variant_images`, `product_variant_tier_pricing`) directly, because product has no write path and
so no outbox (PIN-550). It publishes each table's row changes to `product.public.<table>`, flattened by
`ExtractNewRecordState`: the row's columns are the value, and `__op` and `__source_ts_ms` are added. A delete
arrives with `__deleted: "true"` (a string) and only the row's `id`: the tables use the default replica identity,
so every other column is blank (`""`, `0` or the epoch, not null) and the foreign keys are not there. The
projector finds the owning product from the row it stored earlier. Only product's own projector consumes these
topics.

Unlike the outbox connectors, `snapshot.mode` is `initial`: the first run publishes every existing row, which is
how the seeded catalog reaches MongoDB. `products.search_vector` (a generated `tsvector`) is excluded.
`acme_products`'s `dbz_publication` is created by `product-db-init`, like the cart's.

## Monitoring

View connector logs:
```bash
docker logs -f acme-debezium-connect
```
