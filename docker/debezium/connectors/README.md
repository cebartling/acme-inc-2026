# Debezium Connector Configurations

This directory contains Debezium connector configurations for Change Data Capture (CDC).

## Registering Connectors

After the infrastructure is running, register connectors using the Kafka Connect REST API:

```bash
# Register the PostgreSQL connector
curl -X POST -H "Content-Type: application/json" \
  --data @postgres-connector.json \
  http://localhost:8083/connectors

# List all connectors
curl http://localhost:8083/connectors

# Check connector status
curl http://localhost:8083/connectors/acme-postgres-connector/status

# Delete a connector
curl -X DELETE http://localhost:8083/connectors/acme-postgres-connector
```

## Adding More Connectors

To add CDC for additional databases, create new JSON files following the same pattern
as `postgres-connector.json`, adjusting:

- `name`: Unique connector name
- `database.dbname`: Target database name
- `topic.prefix`: Kafka topic prefix for this database
- `slot.name`: Unique replication slot name

## Monitoring

View connector logs:
```bash
docker logs -f acme-debezium-connect
```
