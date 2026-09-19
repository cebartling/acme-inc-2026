# ADR-0040: Pin Infrastructure Container Image Versions

## Status

Accepted

## Context

Six infrastructure services in `docker-compose.yml` used the `:latest` tag: Confluent Kafka,
Confluent Schema Registry, Debezium Connect, HashiCorp Vault, Prometheus, and Grafana. With
`:latest`, the version running locally is whatever an image pull happened to fetch, so two
developers — or the same developer before and after a pull — can be running different software
from an identical checkout.

PIN-249 is what that looks like in practice. The Schema Registry health check probed the service
with `curl`:

```yaml
test: ["CMD-SHELL", "curl -f http://localhost:8081/subjects || exit 1"]
```

A newer `confluentinc/cp-schema-registry:latest` (resolving to 8.3.2) dropped `curl` from the
image. It ships neither `curl` nor `wget`. Every probe failed from container start onward
(`FailingStreak=951` when the issue was filed) while the registry itself served traffic normally —
`GET /subjects` returned 200 throughout, and the acceptance suite passed 311/311.

Two things went wrong, and they compound:

1. The probe depended on a tool that was never guaranteed to be in the image.
2. `:latest` let that dependency break without any change on our side, and without any signal
   beyond a status flag nobody was blocked by.

The direct cost was low — nothing declares `depends_on: schema-registry: condition:
service_healthy`, so nothing hung. The indirect cost is worse: a service that permanently reads
`unhealthy` is noise that masks a real outage, and the next service to wait on that health
condition would have hung at startup with no obvious cause.

## Decision

### Pin every infrastructure image to an explicit version

All `:latest` tags are replaced with the version that was running and verified working as of
2026-09-19:

| Service          | Was                                      | Now                                    |
|------------------|------------------------------------------|----------------------------------------|
| kafka            | `confluentinc/cp-kafka:latest`           | `confluentinc/cp-kafka:8.3.2`          |
| schema-registry  | `confluentinc/cp-schema-registry:latest` | `confluentinc/cp-schema-registry:8.3.2`|
| debezium-connect | `quay.io/debezium/connect:latest`        | `quay.io/debezium/connect:3.6.3.Final` |
| vault            | `hashicorp/vault:latest`                 | `hashicorp/vault:2.1.1`                |
| prometheus       | `prom/prometheus:latest`                 | `prom/prometheus:v3.14.0`              |
| grafana          | `grafana/grafana:latest`                 | `grafana/grafana:13.2.2`               |

This makes every image in `docker-compose.yml` pinned; PostgreSQL, MongoDB, Redis, Loki, and Tempo
already were.

We pin to **tags, not digests**. Digests are a stronger guarantee — a tag can in principle be
repointed — but they make the diffs unreadable and the file hostile to maintain by hand. Tag-level
pinning removes the failure mode we actually hit, which was a major version moving underneath us.
If tag drift ever bites, digest pinning is the escalation.

### Health checks use tools the image guarantees

The Schema Registry probe now uses `python3`, which is present in the image:

```yaml
test: ["CMD-SHELL", "python3 -c \"import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:8081/subjects',timeout=3).status==200 else 1)\""]
```

Checking for a literal `200` matters: a probe that only confirms the port accepts connections will
report healthy when the registry is listening but erroring. The probe's own 3s timeout sits inside
the health check's 5s timeout, so it fails on its own terms rather than being killed.

`debezium-connect` keeps its `curl` probe — that image genuinely includes `curl`. The general rule
is the point, not the specific tool: **verify the binary exists in the image before a health check
depends on it**, and prefer one the image's own runtime guarantees.

### A health check must be able to fail

PIN-249 exposed a second, quieter variant of the same problem. Loki and Tempo were probed with:

```yaml
test: ["CMD", "/usr/bin/loki", "-version"]
test: ["CMD", "/tempo", "-version"]
```

`-version` prints a version string and exits 0. It does that whether or not the service is serving
traffic, so both containers reported `healthy` while returning 503 on `/ready`. That is worse than
having no health check at all: a check that cannot fail is not a check, it is a false assurance,
and `docker ps` actively lies about the state of the stack.

Both now probe the real readiness endpoint. Each image was verified to contain `wget` first —
Tempo 2.6.1 predates the v2.8.0 distroless switch and carries a full busybox; Loki 3.3.2's
distroless base includes a trimmed busybox at `/busybox/wget`. busybox `wget` exits non-zero on a
non-2xx status, which was confirmed against a 404 path rather than assumed.

`start_period` is 60s for both. Measured cold-start readiness is ~17s (Loki) and ~19s (Tempo), so
this leaves generous headroom for a loaded full-stack start.

The rule, stated once: **a health check must exercise the thing being claimed healthy, and must be
observed failing before it is trusted.**

### Version bumps are deliberate and as-needed

There is no fixed upgrade cadence. Images are bumped when there is a reason — a security fix, a
needed feature, a compatibility requirement — as part of the periodic dependency refresh, and the
stack is brought up and verified as part of that change. The tradeoff is accepted knowingly: we
give up automatic currency to get a reproducible environment.

## Consequences

### Positive

- An identical checkout produces an identical stack for everyone, and over time.
- Upstream changes can no longer silently break local infrastructure. A breaking change now
  arrives as a reviewable diff.
- The Schema Registry reports its true state, so `docker ps` and `docker-manage.sh health-check`
  are trustworthy again.
- `depends_on: condition: service_healthy` on schema-registry becomes viable if ever needed.

### Negative

- Versions now go stale without attention. Security patches in these images will not be picked up
  until someone bumps them.
- Each bump is a real change that needs the stack brought up and verified, rather than arriving
  invisibly.

### Neutral

- The Schema Registry probe is more verbose than the `curl` one-liner it replaces. The inline
  comment in `docker-compose.yml` explains why.
- Loki and Tempo now depend on `wget` being present. A Tempo upgrade to v2.8.0+ moves it to a true
  distroless base, at which point these probes must be re-verified — the same trap that caused
  PIN-249. This is flagged in `documentation/IMPLEMENTATION.md`.
- Historical user-story documents still reference `:latest`. They record what was specified at the
  time and were deliberately left unchanged.

## Implementation Notes

Key files:
- `docker-compose.yml` — image pins and the Schema Registry health check
- `documentation/IMPLEMENTATION.md` — stack listing updated to the pinned Debezium tag

`scripts/docker-manage.sh` also probes the Schema Registry with `curl`, but it runs on the **host**,
not inside the container, so it was unaffected and unchanged.

Verification: both directions of the health check were exercised — the registry reports `healthy`
when up, and flips to `unhealthy` when the registry process inside the container is stopped. A
probe is only correct if it can fail.

## References

- [PIN-249](https://linear.app/pintail-consulting/issue/PIN-249/acme-schema-registry-reports-unhealthy-healthcheck-uses-curl-which-the) — Schema Registry health check uses curl, which the image no longer ships
- [PIN-248](https://linear.app/pintail-consulting/issue/PIN-248) — parent issue this was split from
- [ADR-0007: Apache Kafka for Event Streaming](0007-apache-kafka-event-streaming.md)
- [ADR-0008: Debezium for Change Data Capture](0008-debezium-cdc-connector.md)
