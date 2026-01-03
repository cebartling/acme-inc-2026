# ACME Inc. Docker Infrastructure

This directory contains configuration files for the local development infrastructure.

## Quick Start

```bash
# Start all services
docker compose up -d

# View logs
docker compose logs -f

# Stop all services
docker compose down

# Stop and remove all data
docker compose down -v
```

## Services

| Service | Port | URL | Description |
|---------|------|-----|-------------|
| PostgreSQL | 5432 | - | Command store (CQRS write side) |
| MongoDB | 27017 | - | Query store (CQRS read side) |
| Kafka | 9092 | - | Event streaming |
| Schema Registry | 8081 | http://localhost:8081 | Avro schema management |
| Debezium Connect | 8083 | http://localhost:8083 | CDC connectors |
| Vault | 8200 | http://localhost:8200 | Secrets management |
| Prometheus | 9090 | http://localhost:9090 | Metrics collection |
| Loki | 3100 | http://localhost:3100 | Log aggregation |
| Tempo | 3200 | http://localhost:3200 | Distributed tracing |
| Grafana | 3000 | http://localhost:3000 | Observability dashboards |

## Default Credentials

### PostgreSQL
- Username: `postgres`
- Password: `postgres`
- Application user: `acme_app` / `acme_app_password`

### MongoDB
- Username: `mongo`
- Password: `mongo`
- Application user: `acme_app` / `acme_app_password`

### Grafana
- Username: `admin`
- Password: `admin`

### Vault
- Root token: `dev-root-token`

## Directory Structure

```
docker/
├── debezium/
│   └── connectors/     # Debezium connector configurations
├── grafana/
│   ├── dashboards/     # Pre-built Grafana dashboards
│   └── provisioning/   # Datasource and dashboard provisioning
├── loki/
│   └── config/         # Loki configuration
├── mongodb/
│   └── init/           # MongoDB initialization scripts
├── postgres/
│   └── init/           # PostgreSQL initialization scripts
├── prometheus/
│   └── config/         # Prometheus configuration and rules
├── tempo/
│   └── config/         # Tempo configuration
└── vault/
    ├── config/         # Vault configuration
    └── policies/       # Vault access policies
```

## Registering Debezium Connectors

After the infrastructure is running, register the PostgreSQL connector:

```bash
curl -X POST -H "Content-Type: application/json" \
  --data @docker/debezium/connectors/postgres-connector.json \
  http://localhost:8083/connectors
```

## OpenTelemetry Integration

Services should send telemetry to:
- **Traces**: `http://localhost:4317` (gRPC) or `http://localhost:4318` (HTTP)
- **Logs**: `http://localhost:3100` (Loki)
- **Metrics**: `http://localhost:9090` (Prometheus remote write)

## Customization

Copy `.env.example` to `.env` to customize ports and credentials:

```bash
cp .env.example .env
```
