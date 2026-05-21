---
name: start-local-dev
description: Start the full local development stack via scripts/docker-manage.sh. Use when the user asks to "start local dev", "start the dev stack", "bring up the environment", or invokes /start-local-dev.
---

# start-local-dev

Bring up the full ACME Inc. local development environment using `scripts/docker-manage.sh`.

## Workflow

1. **Resolve the project root.** Use the current working directory if it is the repo root; otherwise locate it with `git rev-parse --show-toplevel`.

2. **Stop any running services.** Run:
   ```bash
   zsh scripts/docker-manage.sh stop
   ```
   This gracefully shuts down both application and infrastructure services if they are up. It is safe to run even when nothing is running — the script handles that case.

3. **Rebuild all application service images.** Run:
   ```bash
   zsh scripts/docker-manage.sh apps-build
   ```
   Always rebuild so local code changes are baked in.

4. **Start infrastructure services.** Run:
   ```bash
   zsh scripts/docker-manage.sh infra-up
   ```

5. **Start application services.** Run:
   ```bash
   zsh scripts/docker-manage.sh apps-up
   ```

6. **Print a banner** confirming the stack is up:
   ```
   ╔══════════════════════════════════════════════╗
   ║   ACME Inc. local dev stack is up and ready  ║
   ╚══════════════════════════════════════════════╝
   ```
   Include a brief summary of key service URLs:
   - Customer Frontend: http://localhost:7600
   - Identity Service:  http://localhost:10300
   - Customer Service:  http://localhost:10301
   - Grafana:           http://localhost:3000
   - Kafka:             localhost:9092
   - PostgreSQL:        localhost:5432
   - MongoDB:           localhost:27017

## Timeouts

- `stop`: 60 000 ms
- `apps-build`: 600 000 ms (Gradle + npm builds are slow)
- `infra-up`: 120 000 ms
- `apps-up`: 300 000 ms

## Guardrails

- Always use `zsh` to invoke the script — it uses zsh-specific syntax.
- Do not skip the stop step even if the user says services are already down; it is idempotent and safe.
- Do not pass `-v`/`--volumes` to stop — preserve data volumes unless the user explicitly asks to wipe them.
- Do not edit the script or compose files as part of this skill — only invoke it.
- If any step exits non-zero, surface the error output and stop; do not proceed to subsequent steps.
- This skill targets Docker. For Podman-based stacks, use `scripts/podman-manage.sh` with the same command names (`stop`, `apps-build`, `infra-up`, `apps-up`).
