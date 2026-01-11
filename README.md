# acme-inc-2026

Demo e-commerce platform for the fictitious ACME, Inc. company.

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Docker | Latest | For containerized services |
| zsh | 5.0+ | Required for shell scripts (default on macOS since Catalina) |
| Node.js | 24+ LTS | For frontend apps and acceptance tests |
| Java | 21+ | For backend services |

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
| Identity Service | 10300 | http://localhost:10300 | Authentication & user management |
| Customer Service | 10301 | http://localhost:10301 | Customer profile management |
| Notification Service | 10302 | http://localhost:10302 | Email notifications |
| Customer Frontend | 7600 | http://localhost:7600 | Customer-facing web application |

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

### Health Checks

The `health` command checks the following services:

**Infrastructure Services:**
| Service | Health Check Method |
|---------|---------------------|
| PostgreSQL | `pg_isready` command |
| Kafka | `kafka-broker-api-versions` command |
| Schema Registry | `GET /subjects` |
| MongoDB | `mongosh db.adminCommand('ping')` |
| Debezium Connect | `GET /connectors` |
| Vault | `GET /v1/sys/health` |

**Observability Services:**
| Service | Health Check Endpoint |
|---------|----------------------|
| Prometheus | `GET /-/healthy` |
| Loki | `GET /ready` |
| Tempo | `GET /ready` |
| Grafana | `GET /api/health` |

**Application Services:**
| Service | Health Check Endpoint |
|---------|----------------------|
| Identity Service | `GET /actuator/health` |
| Customer Service | `GET /actuator/health` |
| Notification Service | `GET /actuator/health` |
| Customer Frontend | `GET /` |

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

### Test Types

| Type | Tags | Description | Browser |
|------|------|-------------|---------|
| UI Tests | `@customer`, `@admin` | Frontend browser automation | Required |
| API Tests | `@api` | Backend HTTP API tests | Not needed |

**Note:** The test hooks conditionally launch Playwright only for UI tests (`@customer` or `@admin` tags). API-only tests skip browser initialization for better performance.

### Acceptance Test Runner Script

A unified shell script is provided for running acceptance tests with automatic report generation.

```bash
./scripts/run-acceptance-tests.sh [options]
./scripts/run-acceptance-tests.sh --help    # Show all available options
```

### Test Runner Options

| Option | Description |
|--------|-------------|
| `--smoke` | Run only smoke tests (@smoke tag) |
| `--regression` | Run full regression suite (@regression tag) |
| `--customer` | Run customer app tests only |
| `--admin` | Run admin app tests only |
| `--api` | Run API tests only (@api tag) |
| `--headed` | Run with visible browser (not headless) |
| `--skip-install` | Skip npm install step |
| `--no-open` | Don't automatically open browser with results |

### Examples

```bash
./scripts/run-acceptance-tests.sh                    # Run all tests, open HTML report
./scripts/run-acceptance-tests.sh --smoke            # Run smoke tests only
./scripts/run-acceptance-tests.sh --headed           # Run with visible browser
./scripts/run-acceptance-tests.sh --api --no-open    # Run API tests, don't open browser
```

### Reports

| Report Type | Location |
|-------------|----------|
| HTML Report | `acceptance-tests/reports/cucumber-report.html` |
| JSON Report | `acceptance-tests/reports/cucumber-report.json` |

### Manual Test Execution

You can also run tests directly with npm:

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
```

### npm Test Scripts

| Script | Description |
|--------|-------------|
| `npm run test` | Run all acceptance tests |
| `npm run test:smoke` | Run smoke test suite (@smoke tag) |
| `npm run test:regression` | Run full regression suite |
| `npm run test:customer` | Run customer app tests only |
| `npm run test:admin` | Run admin app tests only |
| `npm run test:headed` | Run tests with visible browser |

### Configuration

Copy `.env.example` to `.env` to customize test settings:

```bash
cd acceptance-tests
cp .env.example .env
```

### Prerequisites

- **zsh** - Required for shell scripts in `scripts/` (default on macOS since Catalina)
- Node.js 24+ (LTS/Krypton) - the script uses nvm/fnm if available
- Application services should be running (`./scripts/docker-manage.sh start`)

See [documentation/user-stories/0001-acceptance-testing/README.md](documentation/user-stories/0001-acceptance-testing/README.md) for detailed testing documentation.
