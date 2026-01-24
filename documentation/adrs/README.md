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

### Security & Authentication

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0032](0032-sms-mfa-implementation.md) | SMS MFA Implementation Design | Accepted |
| [ADR-0035](0035-jwt-token-implementation.md) | JWT Token Implementation with RS256 | Accepted |
| [ADR-0036](0036-session-management-redis.md) | Session Management with Redis | Accepted |

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
| [ADR-0031](0031-profile-completeness-design.md) | Profile Completeness Tracking Design | Accepted |

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
