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

`customer-outbox.json` routes the customer service's `outbox` table to the topic named in each row's `topic`
column, using Debezium's `EventRouter`. Consumers get the row's `message_key` as the key and its `payload` as a
plain JSON value, and the row's `event_id` in the `id` header.

`snapshot.mode` is `no_data`, so only rows written after the connector starts are published. The default
(`initial`) would re-publish every row already in the table. The cost: if the replication slot is lost, the task
fails instead of skipping rows silently. To recover, stop the connector, `DELETE /connectors/<name>/offsets`,
drop and recreate the slot, then resume; rows written in the gap are not published.

## Monitoring

View connector logs:
```bash
docker logs -f acme-debezium-connect
```
