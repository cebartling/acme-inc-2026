# ADR-0011: Prometheus for Metrics Collection

## Status

Accepted

## Context

A microservices architecture requires comprehensive metrics collection to:

- Monitor service health and performance in real-time
- Detect and alert on anomalies before users are impacted
- Support capacity planning and scaling decisions
- Enable SLO-based alerting for reliability management
- Provide data for post-incident analysis

Candidates considered:
- **Prometheus**: Open-source, pull-based metrics with powerful query language
- **InfluxDB**: Time-series database with push-based collection
- **Datadog**: Managed solution with comprehensive features but higher cost
- **CloudWatch**: AWS-specific, limited query capabilities

## Decision

We will use **Prometheus** as the metrics collection and storage system for all services.

Architecture:
```
┌──────────────────────────────────────────────────────────────┐
│                       Prometheus                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Service Discovery │ Scrape Engine │ TSDB │ PromQL API  │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
         ▲                ▲                ▲
         │ scrape         │ scrape         │ scrape
    ┌────┴────┐      ┌────┴────┐      ┌────┴────┐
    │ Service │      │ Service │      │  Infra  │
    │/metrics │      │/metrics │      │/metrics │
    └─────────┘      └─────────┘      └─────────┘
```

Metrics categories:
- **RED Metrics** (Request-focused):
  - Rate: Requests per second
  - Errors: Failed requests per second
  - Duration: Request latency distribution

- **USE Metrics** (Resource-focused):
  - Utilization: CPU, memory, disk usage
  - Saturation: Queue depths, connection pools
  - Errors: Hardware/resource errors

Standard labels:
```
http_requests_total{service="identity", method="POST", path="/auth/login", status="200"}
http_request_duration_seconds{service="identity", method="POST", path="/auth/login", quantile="0.99"}
```

Spring Boot integration:
- Micrometer library with Prometheus registry
- Actuator endpoint `/actuator/prometheus`
- Standard JVM and Spring metrics auto-configured

## Consequences

### Positive

- **PromQL**: Powerful query language for complex metric analysis
- **Pull Model**: Services don't need to know about monitoring infrastructure
- **Ecosystem**: Extensive exporters, integrations, and community support
- **Alerting**: Built-in alertmanager for sophisticated alert routing
- **Efficiency**: Time-series compression keeps storage costs low
- **Standards**: OpenMetrics format widely supported

### Negative

- **High Availability**: Prometheus is not natively distributed
- **Long-Term Storage**: Local storage has retention limits
- **Cardinality**: High cardinality labels can cause performance issues
- **Pull Model Limits**: Challenging for short-lived jobs or serverless

### Mitigations

- Use Prometheus federation or Thanos for HA and long-term storage
- Establish label cardinality guidelines (avoid UUIDs, user IDs as labels)
- Use pushgateway for batch jobs when necessary
- Monitor Prometheus memory and storage usage
