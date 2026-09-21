---
name: stop-local-dev
description: Stop the full local development stack via scripts/docker-manage.sh, removing volumes and the network. Use when the user asks to "stop local dev", "tear down the dev stack", "shut down the environment", or invokes /stop-local-dev.
---

# stop-local-dev

Bring down the full ACME Inc. local development environment using `scripts/docker-manage.sh`, removing data volumes and the Docker network.

## Workflow

1. **Resolve the project root.** Use the current working directory if it is the repo root; otherwise locate it with `git rev-parse --show-toplevel`.

2. **Stop application services.** Run:
   ```bash
   zsh scripts/docker-manage.sh apps-down
   ```
   This stops and removes application containers (identity, customer, notification services, frontend, db-init jobs). Safe to run even if apps are already down.

3. **Stop infrastructure services and remove volumes and network.** Run:
   ```bash
   zsh scripts/docker-manage.sh infra-down -v
   ```
   The `-v` flag removes named volumes (Postgres, MongoDB, Redis, Kafka, Vault, observability data). The Docker network (`acme-network`) is removed automatically by compose as part of infra teardown.

4. **Confirm teardown** by reporting what was stopped and that volumes and network have been removed.

## Timeouts

- `apps-down`: 60 000 ms
- `infra-down -v`: 60 000 ms

## Guardrails

- Always use `zsh` to invoke the script — it uses zsh-specific syntax.
- Always stop apps before infra — stopping infra first while apps are running can leave orphaned containers.
- The `-v` flag permanently deletes all data volumes. **Do not omit it** — this skill's purpose is a clean teardown including volume removal. If the user wants to preserve data, they should use a different command and not invoke this skill.
- Do not edit the script or compose files as part of this skill — only invoke it.
- If either step exits non-zero, surface the error output immediately; do not proceed to the next step.
- This skill targets Docker. For Podman-based stacks, use `scripts/podman-manage.sh` with the same command names (`apps-down`, `infra-down -v`).
