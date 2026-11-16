# ADR-0064: AWS Demo Deployment

## Status

Accepted

## Context

ACME Inc. 2026 runs only on a laptop, through Docker Compose ([ADR-0016](0016-docker-compose-development.md)),
or shared from it over Tailscale. That ties every demo to one machine. An AWS demo environment
gives the demo a public HTTPS URL that works from anywhere.

The environment is for demos only:

- No high-availability or backup requirements. Data starts fresh with each deploy.
- It covers 5 of the 6 backend services (identity, customer, notification, product,
  shopping-cart), the customer frontend and the admin frontend. The order service isn't covered
  yet (see Open Decisions).
- One manual GitHub Actions run creates it from scratch and proves it works. A second run
  destroys it.

The decisions below were made in [PIN-533](https://linear.app/pintail-consulting/issue/PIN-533/deploy-acme-inc-2026-to-an-on-demand-aws-demo-environment).
Staging and production environments, multi-AZ and backups are out of scope. Local development
doesn't change.

## Decision

### 1. Compute: ECS on Fargate

The existing container images run as ECS services on Fargate. There are no servers to manage,
ECS replaces failed tasks, and tasks run in private subnets next to the data tier.

**Rejected:**

- **EKS:** much more to run for a demo.
- **EC2 with Docker Compose:** no self-healing or scaling.
- **App Runner:** weak private networking to the data tier.

### 2. Infrastructure as code: Terraform / OpenTofu

Terraform (or OpenTofu) describes the environment, with remote state in S3. `plan`, `apply` and
`destroy` map directly onto creating and destroying the environment for each demo, and OpenTofu
keeps an open-source build of the same tool available.

The code is split into two stacks, so destroying a demo doesn't remove things that are slow or
awkward to recreate:

- **Foundation (long-lived):** the Terraform state bucket, the GitHub OIDC role, ECR
  repositories, the Route 53 hosted zone, ACM certificates, third-party secrets (SendGrid,
  Twilio), the Managed Grafana and Managed Prometheus workspaces, and an AWS Budgets alert.
- **Demo environment (per demo):** the VPC with a NAT gateway, the ECS cluster and services, the
  load balancer, RDS, DocumentDB, ElastiCache, MSK, MSK Connect, and per-deploy secrets
  (database passwords, the test API key).

The demo stack must be fully destroyable: no deletion protection, and no final snapshots for
RDS or DocumentDB.

**Rejected:**

- **AWS CDK:** adds a program in a general-purpose language to build, and still deploys through
  CloudFormation.
- **CloudFormation:** verbose templates, and AWS only.
- **Pulumi:** adds a general-purpose language and its runtime, and is less widely known than
  Terraform.

### 3. Data tier: AWS managed services

Every infrastructure component runs as an AWS managed service:

| Local (Docker Compose) | AWS demo |
|------------------------|----------|
| PostgreSQL | Amazon RDS for PostgreSQL |
| MongoDB | Amazon DocumentDB 8.0 (decision 4) |
| Redis | Amazon ElastiCache, node-based |
| Kafka | Amazon MSK Provisioned (decision 5) |
| HashiCorp Vault | AWS Secrets Manager. [ADR-0009](0009-hashicorp-vault-secrets.md) still applies locally, where Vault isn't integrated yet and services read credentials from environment variables. |
| Schema Registry | Not deployed. Events are JSON ([ADR-0008](0008-debezium-cdc-connector.md), amended in PIN-534). |
| Debezium (Kafka Connect) | Amazon MSK Connect, one connector per service database, with logical replication on in RDS. **Still open**, see Open Decisions. |

Managed services need no containers, volumes or upgrades of our own for the data tier, and RDS
and DocumentDB can be destroyed along with the demo.

**Rejected:**

- **Self-hosted containers on Fargate:** Fargate tasks have no persistent local storage, so
  Kafka, PostgreSQL and MongoDB would need EFS volumes plus their own health checks and
  restart handling.
- **A mixed approach:** two ways to run, monitor and secure the data tier.

### 4. MongoDB: Amazon DocumentDB

MongoDB read models move to DocumentDB 8.0, inside the VPC and from the same vendor as the rest
of the environment. Today only customer has a read model, and it only saves and removes
documents, so the compatibility risk is low. PIN-534 adds read models and queries to four more
services ([ADR-0006](0006-mongodb-query-store.md)), and each needs the same connection change
(PIN-538) and a DocumentDB compatibility check.

**Rejected:**

- **MongoDB Atlas:** a second vendor, and private networking to the VPC needs a paid tier.

### 5. Kafka: Amazon MSK Provisioned

MSK Provisioned with the smallest brokers (`kafka.t3.small`). The services keep their current
Kafka client configuration.

**Rejected:**

- **MSK Serverless:** it requires IAM authentication, which means changes in every service.

### 6. Hostnames: one API host with path routing

The domain lives in Route 53. Each frontend gets its own subdomain. The five backends share one
API hostname (`api.<domain>`), and load balancer path rules send each request to the right
service. Identity's sign-in cookie only goes back to the host that set it, so with one API host
every service receives it with no code change.

**Rejected:**

- **One subdomain per backend:** identity's host-only sign-in cookie wouldn't reach the other
  services, which breaks the signed-in cart.

### 7. Lifecycle: create and destroy per demo

The environment is created before each demo and destroyed afterwards, so it costs almost
nothing between demos. Left running, it would cost roughly $350–500 a month with the smallest
MSK brokers, plus roughly $80 a month for each MSK Connect connector.

**Rejected:**

- **Always on:** pays the full monthly cost for an environment used only during demos.
- **Off-hours scaling:** scaling the ECS services down still leaves the MSK brokers, the NAT
  gateway and the databases billing by the hour.

### 8. Delivery: manual GitHub Actions workflows with OIDC

Two workflows, each started by hand (`workflow_dispatch`):

- **Deploy demo:** build and push the images, `apply` the demo stack, roll out ECS, and run the
  acceptance smoke suite against the new environment. The test APIs are turned on there,
  guarded by a random test API key generated for each deploy.
- **Destroy demo:** `destroy` the demo stack.

The workflows reach AWS through GitHub OIDC, with no long-lived keys. The only step run from a
laptop is the first foundation apply, because the state bucket and the OIDC role don't exist
yet.

**Rejected:**

- **Deploy on merge to `main`:** there's no environment to deploy to between demos, and each
  deploy creates MSK and DocumentDB, which takes half an hour or more.
- **Deploy from a laptop:** it needs long-lived AWS keys on one machine, and ties every deploy
  to that machine again.

### 9. Observability: AWS managed services through ADOT

- **Metrics:** Amazon Managed Service for Prometheus, with dashboards in Amazon Managed Grafana.
  PromQL and Grafana dashboards stay as they are locally ([ADR-0011](0011-prometheus-metrics.md),
  [ADR-0012](0012-grafana-dashboards.md)).
- **Logs:** CloudWatch Logs.
- **Traces:** AWS X-Ray, through the AWS Distro for OpenTelemetry (ADOT) collector.

[ADR-0011](0011-prometheus-metrics.md), [ADR-0013](0013-loki-log-aggregation.md) and
[ADR-0014](0014-tempo-distributed-tracing.md) rejected CloudWatch and X-Ray for being
AWS-specific. That still holds for local development, which keeps Loki and Tempo. The demo
environment runs only on AWS, so being AWS-specific costs nothing there.

**Rejected:**

- **Running the local stack (Prometheus, Grafana, Loki, Tempo) on Fargate:** more containers to
  run and size, with the same storage problem as a self-hosted data tier.
- **The X-Ray SDKs and the X-Ray daemon:** they are in maintenance mode, with end of support on
  2027-02-25. ADOT and OpenTelemetry are the supported path.

## Open Decisions

These are still open, on PIN-533 unless noted. This ADR doesn't decide them.

- **Order service.** The repo has a sixth backend, order, which the customer frontend's
  checkout calls. PIN-533 doesn't cover it. Deploy it, or leave checkout out of the demo?

- **Debezium hosting.** MSK Connect bills each connector separately, roughly $80 a month each
  while running. One self-hosted Kafka Connect worker on Fargate could run all five connectors
  for much less and keeps the Kafka Connect REST API, but it breaks the "all AWS managed"
  decision. Keep MSK Connect?
- **AWS account and region.**
- **Domain:** which Route 53 domain, and the names of the two frontend subdomains and the API
  host.
- **Managed Grafana sign-in:** it uses IAM Identity Center (or SAML). Is IAM Identity Center set
  up in the account?
- **Email sender:** real SendGrid sending needs a verified sender or domain. The sender
  defaults to `noreply@acme.com`, and SendGrid's sandbox mode delivers nothing, so a person
  couldn't verify their email. Which sender address, on which domain?
- **SMS:** use real Twilio, or keep the `mock` provider?
- **Budgets alert threshold:** what monthly amount?
- **Notification lookup endpoints:** the smoke suite reads notification's
  `/api/v1/notifications` endpoints, so they'll be public on the demo environment, with no auth
  and no test-key guard. Anyone could learn whether an email address has an account. Accept that
  for a short-lived demo, or guard them with the test API key?

## Consequences

### Positive

- A demo gets a public HTTPS URL that works from anywhere, not just from one laptop.
- The environment costs almost nothing between demos.
- GitHub holds no long-lived AWS keys.
- Every deploy proves itself by running the acceptance smoke suite.

### Negative

- A deploy takes roughly half an hour or more, because creating MSK and DocumentDB is slow.
  It has to start well before a demo.
- The services need code changes before they run on AWS:
  - **Identity's JWT signing keys** (PIN-536). Identity creates its keys in memory at startup
    ([ADR-0035](0035-jwt-token-implementation.md)), so a second identity task can't verify the
    first task's tokens, and every restart signs everyone out. The keys come from Secrets
    Manager instead. Local Docker Compose development must keep working.
  - **Metrics** (PIN-537). Only order has the Prometheus registry
    ([ADR-0011](0011-prometheus-metrics.md)). The other five services need it, and product and
    shopping-cart don't expose the Prometheus endpoint.
  - **DocumentDB connection** (PIN-538). The customer service connects to MongoDB with only a
    host, port and database name. DocumentDB needs credentials, TLS and the AWS certificate
    bundle in the image.
  - **Admin frontend** (PIN-539). It needs a container image and a health route.
- Some configuration breaks the demo if it's missed, with no code change to catch it:
  - The customer frontend's backend URLs are baked in at build time and must point at the API
    host.
  - CORS on the four browser-facing services (identity, customer, product, shopping-cart) must
    allow the frontend origins, and so must order if it's deployed.
  - Notification's email links and identity's `FRONTEND_BASE_URL` must point at the demo URLs.
  - The services use plaintext Kafka, so MSK must accept plaintext inside the VPC (or the
    services switch to SSL through an environment variable). MSK turns topic auto-creation off
    by default, and no code creates topics, so a custom MSK configuration turns it on (or the
    topics are created explicitly).
  - The services connect to Redis without TLS, so ElastiCache runs without in-transit
    encryption (or Redis TLS is turned on through an environment variable).
  - The services already export OTLP traces ([ADR-0014](0014-tempo-distributed-tracing.md),
    PIN-370), so `OTEL_EXPORTER_OTLP_ENDPOINT` must point at the ADOT collector.
  - `TEST_API_ENABLED=true` and the test API key are set on the demo environment only.
- Logs and traces live in different tools on AWS (CloudWatch, X-Ray) than locally (Loki,
  Tempo).

## References

- [PIN-533](https://linear.app/pintail-consulting/issue/PIN-533/deploy-acme-inc-2026-to-an-on-demand-aws-demo-environment):
  Deploy ACME Inc. 2026 to an on-demand AWS demo environment. Its sub-issues are PIN-535 to
  PIN-544.
- [PIN-534](https://linear.app/pintail-consulting/issue/PIN-534/wire-debezium-cdc-into-cqrs-for-all-services):
  Wire Debezium CDC into CQRS for all services.
- [PIN-535](https://linear.app/pintail-consulting/issue/PIN-535/aws-demo-adr-for-the-deployment-decisions):
  this ADR.
- [ADR-0004](0004-change-data-capture.md) and [ADR-0008](0008-debezium-cdc-connector.md): change
  data capture and Debezium.
- [ADR-0006](0006-mongodb-query-store.md): MongoDB as query store.
- [ADR-0009](0009-hashicorp-vault-secrets.md): HashiCorp Vault for secrets management.
- [ADR-0011](0011-prometheus-metrics.md), [ADR-0012](0012-grafana-dashboards.md),
  [ADR-0013](0013-loki-log-aggregation.md), [ADR-0014](0014-tempo-distributed-tracing.md): the
  local observability stack.
- [ADR-0016](0016-docker-compose-development.md): Docker Compose for local development.
- [X-Ray daemon end of support](https://docs.aws.amazon.com/xray/latest/devguide/xray-daemon-eos.html).
