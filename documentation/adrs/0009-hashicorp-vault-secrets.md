# ADR-0009: HashiCorp Vault for Secrets Management

## Status

Accepted

## Context

A microservices architecture has numerous secrets to manage:

- Database credentials for PostgreSQL and MongoDB
- API keys for external services (payment processors, shipping carriers)
- Encryption keys for sensitive data
- Service-to-service authentication tokens
- TLS certificates

Traditional approaches have significant drawbacks:
- **Environment Variables**: Secrets visible in process listings, logs, and crash dumps
- **Config Files**: Secrets in version control or require secure distribution
- **Cloud Provider Secret Managers**: Vendor lock-in and varying feature sets

We need a secrets management solution that:

- Provides centralized, audited access to secrets
- Supports dynamic credential generation
- Enables secret rotation without service restarts
- Offers fine-grained access control
- Works across development, staging, and production environments

## Decision

We will use **HashiCorp Vault** as the centralized secrets management platform.

Architecture:
```
┌─────────────────────────────────────────────────────────────┐
│                         Vault                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   Secret    │  │  Database   │  │    PKI Engine       │  │
│  │   Engine    │  │  Engine     │  │   (Certificates)    │  │
│  │  (KV v2)    │  │ (Dynamic)   │  │                     │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└────────────────────────────────────────────────────────────-┘
         ▲                 ▲                    ▲
         │                 │                    │
    ┌────┴────┐       ┌────┴────┐          ┌────┴────┐
    │ Service │       │ Service │          │ Service │
    │    A    │       │    B    │          │    C    │
    └─────────┘       └─────────┘          └─────────┘
```

Secret engines in use:
- **KV v2**: Static secrets with versioning (API keys, encryption keys)
- **Database**: Dynamic credentials for PostgreSQL and MongoDB
- **PKI**: TLS certificate generation and rotation

Authentication methods:
- **Kubernetes Auth**: Services authenticate via service account tokens
- **AppRole**: CI/CD pipelines and non-Kubernetes workloads
- **Token**: Development and emergency access

Policy structure:
```hcl
# Example: identity-service policy
path "secret/data/identity/*" {
  capabilities = ["read"]
}

path "database/creds/identity-service" {
  capabilities = ["read"]
}
```

## Consequences

### Positive

- **Centralized Audit**: All secret access is logged and auditable
- **Dynamic Credentials**: Database credentials generated on-demand with TTL
- **Rotation**: Secrets can be rotated without application restarts
- **Fine-Grained Access**: Policies control exactly which secrets each service can access
- **Encryption as a Service**: Transit engine for application-level encryption

### Negative

- **Operational Complexity**: Vault itself is a critical piece of infrastructure
- **High Availability Requirements**: Vault outage blocks secret access
- **Learning Curve**: Policies and engines require understanding
- **Development Overhead**: Applications must integrate Vault client libraries

### Mitigations

- Run Vault in HA mode with proper backup procedures
- Cache secrets with appropriate TTL in applications
- Use Spring Cloud Vault for seamless integration in Spring Boot services
- Provide development mode configuration for local development
- Document common patterns and troubleshooting procedures
