# Architecture Decision Records

This directory contains Architecture Decision Records (ADRs) for the ACME Inc. e-commerce platform. ADRs capture important architectural decisions made during the project, including the context, decision rationale, and consequences.

## What is an ADR?

An Architecture Decision Record documents a significant architectural decision, including:
- **Context**: The situation and forces at play
- **Decision**: The chosen approach
- **Consequences**: The resulting impacts, both positive and negative

## ADR Index

### Core Architecture Patterns

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0001](0001-microservices-architecture.md) | Microservices Architecture | Accepted |
| [ADR-0002](0002-cqrs-pattern.md) | Command Query Responsibility Segregation (CQRS) | Accepted |
| [ADR-0003](0003-event-sourcing.md) | Event Sourcing | Accepted |
| [ADR-0004](0004-change-data-capture.md) | Change Data Capture (CDC) | Accepted |
| [ADR-0018](0018-idempotent-event-processing.md) | Idempotent Event Processing Pattern | Accepted |
| [ADR-0022](0022-transactional-outbox-pattern.md) | Transactional Outbox Pattern for Event Publishing | Accepted |
| [ADR-0023](0023-kafka-consumer-group-patterns.md) | Kafka Consumer Group Patterns for Multi-Event Topics | Accepted |
| [ADR-0024](0024-synchronous-customer-lookup-for-notifications.md) | Synchronous Customer Lookup for Welcome Notifications | Accepted |
| [ADR-0041](0041-per-service-persistence-model.md) | Per-Service Persistence Model | Accepted |
| [ADR-0042](0042-best-effort-event-publishing.md) | Best-Effort Event Publishing in Cart and Product | Superseded for the cart by [ADR-0060](0060-cart-events-through-outbox.md) |
| [ADR-0044](0044-forward-customer-credentials-between-services.md) | Service-to-Service Calls Carry the Customer's Credentials | Accepted, amended by [ADR-0061](0061-order-service-token-for-cart-calls.md) |
| [ADR-0045](0045-cart-product-calls-fail-closed.md) | Cart Calls the Product Service Synchronously and Fails Closed | Accepted |
| [ADR-0050](0050-api-error-body-format.md) | API Error Response Body Format | Accepted |
| [ADR-0061](0061-order-service-token-for-cart-calls.md) | The Order Service Authenticates to the Cart with a Shared Secret | Accepted |

### Shopping Cart

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0046](0046-guest-cart-identity-and-merge.md) | Guest Cart Identity and Merge on Sign-In | Accepted |
| [ADR-0047](0047-cart-checkout-lock-and-session.md) | Cart Checkout Lock and Checkout Session Lifecycle | Accepted |
| [ADR-0048](0048-cart-optimistic-locking.md) | Optimistic Locking with One Retry for Cart Changes | Accepted |
| [ADR-0049](0049-cart-data-lifecycle-jobs.md) | Cart Data Lifecycle Through Scheduled Jobs | Accepted |
| [ADR-0060](0060-cart-events-through-outbox.md) | Cart Events Through the Transactional Outbox | Accepted |

### Product Catalog and Inventory

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0053](0053-search-on-postgresql-full-text.md) | Product Search on PostgreSQL Full-Text Search | Accepted |
| [ADR-0056](0056-money-representation.md) | Money as NUMERIC(10,2) and BigDecimal Without Currency | Accepted |
| [ADR-0058](0058-inventory-as-in-stock-flag.md) | Inventory as an In-Stock Flag Without Reservation | Accepted |

### Technology Stack

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0005](0005-postgresql-command-store.md) | PostgreSQL as Command Store | Accepted |
| [ADR-0006](0006-mongodb-query-store.md) | MongoDB as Query Store | Accepted |
| [ADR-0007](0007-apache-kafka-event-streaming.md) | Apache Kafka for Event Streaming | Accepted |
| [ADR-0008](0008-debezium-cdc-connector.md) | Debezium for Change Data Capture | Accepted |
| [ADR-0009](0009-hashicorp-vault-secrets.md) | HashiCorp Vault for Secrets Management | Accepted |
| [ADR-0010](0010-kotlin-spring-boot-backend.md) | Kotlin with Spring Boot for Backend Services | Accepted |
| [ADR-0017](0017-sendgrid-transactional-email.md) | SendGrid for Transactional Email | Accepted |
| [ADR-0040](0040-container-image-version-pinning.md) | Pin Infrastructure Container Image Versions | Accepted |

### Security & Authentication

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0019](0019-persistent-rate-limiting.md) | Persistent Rate Limiting for Verification Resend | Accepted |
| [ADR-0021](0021-information-disclosure-prevention.md) | Information Disclosure Prevention | Accepted |
| [ADR-0028](0028-append-only-consent-records.md) | Append-Only Consent Records for GDPR Compliance | Accepted |
| [ADR-0031](0031-credential-validation-security.md) | Credential Validation Security Patterns | Accepted |
| [ADR-0032](0032-account-lockout-mechanism.md) | Account Lockout Mechanism | Accepted |
| [ADR-0033](0033-mfa-totp-implementation.md) | MFA TOTP Implementation | Accepted |
| [ADR-0035](0035-jwt-token-implementation.md) | JWT Token Implementation with RS256 | Accepted |
| [ADR-0036](0036-session-management-redis.md) | Session Management with Redis | Accepted |
| [ADR-0038](0038-sms-mfa-implementation.md) | SMS MFA Implementation Design | Accepted |
| [ADR-0039](0039-device-trust-implementation.md) | Device Trust Implementation for MFA Bypass | Accepted |
| [ADR-0043](0043-cross-service-jwt-verification.md) | Cross-Service JWT Verification with JWKS | Accepted |

### Backend Code Patterns

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0020](0020-sealed-classes-use-case-results.md) | Sealed Classes for Use Case Results | Accepted |
| [ADR-0029](0029-arrow-functional-programming.md) | Arrow for Functional Programming in Kotlin | Accepted |
| [ADR-0054](0054-independent-service-builds.md) | Independent Per-Service Builds with No Shared Code | Accepted |
| [ADR-0055](0055-jackson-versions-split.md) | Jackson 3 for HTTP, Jackson 2 for Kafka, Redis and JSONB | Accepted |
| [ADR-0057](0057-identifier-generation.md) | Identifier Generation | Accepted |
| [ADR-0059](0059-open-in-view-setting.md) | JPA open-in-view Off Except in Product | Accepted |

### Observability

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0011](0011-prometheus-metrics.md) | Prometheus for Metrics Collection | Accepted |
| [ADR-0012](0012-grafana-dashboards.md) | Grafana for Observability Dashboards | Accepted |
| [ADR-0013](0013-loki-log-aggregation.md) | Loki for Log Aggregation | Accepted |
| [ADR-0014](0014-tempo-distributed-tracing.md) | Tempo for Distributed Tracing | Accepted |

### Testing and Development

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0015](0015-cucumber-playwright-testing.md) | Cucumber.js and Playwright for Acceptance Testing | Accepted |
| [ADR-0016](0016-docker-compose-development.md) | Docker Compose for Local Development | Accepted |
| [ADR-0027](0027-acceptance-testing-agentic-development.md) | Acceptance Testing as a Safety Net for Agentic Development | Accepted |

### Frontend Patterns

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0025](0025-multi-step-wizard-pattern.md) | Multi-Step Wizard Pattern for Profile Completion | Accepted |
| [ADR-0026](0026-phone-number-validation.md) | Phone Number Validation | Accepted |
| [ADR-0030](0030-signin-form-reusable-patterns.md) | Signin Form Reusable Component Patterns | Accepted |
| [ADR-0037](0037-profile-completeness-design.md) | Profile Completeness Tracking Design | Accepted |
| [ADR-0051](0051-client-side-search-circuit-breaker.md) | Client-Side Circuit Breaker for Product Search | Accepted |
| [ADR-0052](0052-frontend-stack-and-state-management.md) | Frontend Stack and State Management | Accepted |

## ADR Status Definitions

- **Proposed**: Under discussion, not yet accepted
- **Accepted**: Decision has been made and is in effect
- **Deprecated**: Decision is being phased out
- **Superseded**: Replaced by a newer ADR (reference provided)

## Creating New ADRs

When making a significant architectural decision:

1. Copy the template below to a new file: `NNNN-descriptive-title.md`
2. Fill in the sections with relevant details
3. Submit for review via pull request
4. Update this README index when accepted

### ADR Template

```markdown
# ADR-NNNN: Title

## Status

Proposed | Accepted | Deprecated | Superseded by [ADR-XXXX](xxxx-title.md)

## Context

Describe the situation, the forces at play, and why a decision is needed.

## Decision

State the decision that was made.

## Consequences

### Positive

- List the positive impacts

### Negative

- List the negative impacts

### Mitigations

- Describe how negative consequences will be addressed
```

## References

- [Documenting Architecture Decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions) - Michael Nygard
- [ADR GitHub Organization](https://adr.github.io/)
