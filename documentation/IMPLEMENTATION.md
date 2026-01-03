# Implementation Guidelines


## Core infrastructure

- Use Docker Compose to manage infrastructure services
    - YAML file in repository root directory
    - Dependency for all backend services and frontend applications 
    - Initialization scripts should be managed under the `docker` directory 
- PostgreSQL 16+ for read-write, command store
    - Use a Debezium-sourced image that has logical replication enabled and replication slots configured 
    - `quay.io/debezium/postgres:latest`
- MongoDB 8.2+ for read-only, query store
- Hashicorp Vault for secrets management
- Confluent Kafka for events and messaging
- Confluent Schema Registry for Avro schemas for Kafka messaging
- Debezium Kafka Connect connector for change data capture on PostgreSQL tables
    - `quay.io/debezium/connect:latest`
- Grafana for observability dashboards
- Tempo for OTel tracing backend
- Loki for OTel logging via OTel Collector
- Prometheus for OTel metrics via OTel SDK/Collector

### Distroless container images (Tempo and Loki)

Grafana Tempo (since v2.8.0) and Loki use Google's distroless base container images. These are minimal images that contain only the application and its runtime dependencies—no shells, package managers, or unnecessary system utilities.

**Security benefits:**

- **Reduced attack surface**: Without shells (`/bin/sh`, `/bin/bash`) or package managers, attackers who breach the container have fewer tools to exploit for lateral movement or privilege escalation
- **Fewer CVEs**: Minimal components mean fewer vulnerabilities to track and patch
- **Non-root execution**: Both run as UID `10001` (user `tempo`/`loki`) rather than root, following the principle of least privilege
- **Read-only filesystem**: Containers enforce read-only root filesystems and drop all Linux capabilities

**Operational considerations:**

- **No shell access for debugging**: Use `kubectl debug` or ephemeral containers for troubleshooting
- **Multi-stage builds**: Application artifacts are built in full-featured containers and copied to distroless runtime images
- **File permissions**: When upgrading from older versions, existing data files may need ownership changes to UID `10001`

**Image sizes:**

- Distroless static images are ~2 MiB (vs ~124 MiB for Debian, ~5 MiB for Alpine)
- Smaller images mean faster pull times and reduced storage costs


## Backend services

- Kotlin 2.2
- Java 24
    - Project Loom: virtual threads usage
- Spring Boot 4
    - Spring MVC
    - Blocking I/O
    - JPA
    - Actuator metrics
- Gradle build infrastructure
- SDKMAN Java runtime management
- PostgreSQL for read-write, command store
- MongoDB for read-only, query store
- OpenTelemtery for distributed tracing, metrics and structured logging
- Spring Actuator health check endpoints
- REST endpoints
- Kafka for messaging
- JUnit 6 and Mockito for unit testing


### CQRS implementation

- Use saga pattern for reversible business transactions when implementing commands
- The workflow orchestrator service will house the saga pattern-based workflows
- Debezium Kafka Connect connector to implement change data capture between command store and query store

### Service projects

- Identity management: `/backend-services/identity`
- Product catalog: `/backend-services/product`
- Inventory management: `/backend-services/inventory`
- Customer management: `/backend-services/customer`
- Order management: `/backend-services/order`
- Notifications management: `/backend-services/notification`
- Shipping and fulfillment management: `/backend-services/fulfillment`
- Payment management: `/backend-services/payment`
- Pricing management: `/backend-services/pricing`
- Shopping cart management: `/backend-services/shopping-cart`
- Analytics: `/backend-services/analytics`


## Frontend applications

- Tanstack Start
- React 19.2
- Vite
- Tailwind CSS 4 for styling
- shadcn/ui UI components
- shadcn icons
- Zod for schemas, validation
- Zustand for state management
- React Hook Form for user input forms
- TanStack Query for data fetching
- TanStack Table and Virtual for data tables
- TanStack Router for routing
- Mock Service Worker (msw) for API endpoint mocking
- Vitest for unit testing

### Application projects

- Customer-facing: `/frontend-apps/customer`
- Admin-facing: `/frontend-apps/admin`

## Acceptance testing

- User story acceptance criteria will be used to craft automated acceptance tests
- Cucumber.js should be used for automating acceptance tests
- Playwright should be used for browser automation
- Acceptance tests project will reside in the `acceptance-tests` directory.
- Use Allure Report 3 for HTML Cucumber test run reporting
    - https://allurereport.org/docs/v3/ 