# acme-inc-2026

Demo e-commerce platform for the fictitious ACME, Inc. company.

## Local Development Infrastructure

The platform uses Docker Compose to provide a fully containerized local development environment.

### Quick Start

```bash
# Start all services
docker compose up -d

# View service status
docker compose ps

# View logs
docker compose logs -f

# Stop all services
docker compose down

# Stop and remove all data
docker compose down -v
```

### Services

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

### Default Credentials

| Service | Username | Password |
|---------|----------|----------|
| Grafana | admin | admin |
| Vault | - | dev-root-token |

### Configuration

Copy `.env.example` to `.env` to customize ports and credentials:

```bash
cp .env.example .env
```

See [docker/README.md](docker/README.md) for detailed infrastructure documentation.

## Application Services

Application containers are managed separately from infrastructure using `docker-compose.apps.yml`.

### Quick Start

```bash
# 1. Ensure infrastructure is running
docker compose up -d

# 2. Build and start application services
docker compose -f docker-compose.apps.yml up --build --force-recreate -d

# 3. Cleanup old images
docker image prune -f

# Stop application services
docker compose -f docker-compose.apps.yml down --remove-orphans
```

### Services

| Service | Port | URL | Description |
|---------|------|-----|-------------|
| Identity Service | 8080 | http://localhost:8080 | Authentication & user management |
| Customer Frontend | 5173 | http://localhost:5173 | Customer-facing web application |

### Building Individual Services

```bash
# Rebuild only identity service
docker compose -f docker-compose.apps.yml up --build --force-recreate -d identity-service

# Rebuild only customer frontend
docker compose -f docker-compose.apps.yml up --build --force-recreate -d customer-frontend
```

### Container Images

Images can also be built directly using Podman or Docker:

```bash
# Identity Service
cd backend-services/identity
podman build -f Containerfile -t acme-identity-service .

# Customer Frontend
cd frontend-apps/customer
podman build -f Containerfile -t acme-customer-frontend .
```

## Docker Management Script

A unified shell script is provided for managing all Docker services.

### Usage

```bash
./scripts/docker-manage.sh <command> [options]
./scripts/docker-manage.sh help    # Show all available commands
```

### Common Commands

| Command | Description |
|---------|-------------|
| `start` | Start all services (infrastructure + applications) |
| `stop` | Stop all services |
| `restart` | Restart all services |
| `status` | Show status of all services |
| `health` | Check health of all services |

### Infrastructure Commands

| Command | Description |
|---------|-------------|
| `infra-up` | Start infrastructure services |
| `infra-down` | Stop infrastructure services |
| `infra-status` | Show infrastructure status |
| `infra-logs` | View infrastructure logs |

### Application Commands

| Command | Description |
|---------|-------------|
| `apps-up` | Build and start application services |
| `apps-down` | Stop application services |
| `apps-status` | Show application status |
| `apps-logs` | View application logs |
| `rebuild <service>` | Rebuild and restart a specific service |

### Cleanup Commands

| Command | Description |
|---------|-------------|
| `cleanup` | Remove unused Docker images |
| `cleanup-all` | Full system prune (with confirmation) |
| `cleanup-volumes` | Remove all data volumes (with confirmation) |
| `teardown` | Full teardown: stop all services, remove volumes/networks/orphans |

### Examples

```bash
./scripts/docker-manage.sh start                  # Start everything
./scripts/docker-manage.sh apps-logs -f           # Follow application logs
./scripts/docker-manage.sh shell postgres         # Open shell in postgres container
./scripts/docker-manage.sh rebuild identity-service
./scripts/docker-manage.sh health                 # Check all service health
```

## Acceptance Testing

The platform includes a BDD acceptance testing framework using Cucumber.js and Playwright.

### Quick Start

```bash
cd acceptance-tests

# Install dependencies
npm install

# Install Playwright browsers
npx playwright install

# Run all tests
npm run test

# Run smoke tests only
npm run test:smoke

# Run tests with visible browser
npm run test:headed

# Generate and view Allure report
npm run allure:serve
```

### Test Scripts

| Script | Description |
|--------|-------------|
| `npm run test` | Run all acceptance tests |
| `npm run test:smoke` | Run smoke test suite (@smoke tag) |
| `npm run test:regression` | Run full regression suite |
| `npm run test:customer` | Run customer app tests only |
| `npm run test:admin` | Run admin app tests only |
| `npm run test:headed` | Run tests with visible browser |
| `npm run allure:serve` | Generate and open Allure report |

### Configuration

Copy `.env.example` to `.env` to customize test settings:

```bash
cd acceptance-tests
cp .env.example .env
```

See [documentation/user-stories/0001-acceptance-testing/README.md](documentation/user-stories/0001-acceptance-testing/README.md) for detailed testing documentation.
