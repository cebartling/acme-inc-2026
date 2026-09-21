---
name: stop-local-dev
description: Stop the full local development stack via scripts/docker-manage.sh, optionally removing the Docker network and data volumes. Use when the user asks to "stop local dev", "tear down the dev stack", "shut down the environment", or invokes /stop-local-dev.
---

# stop-local-dev

Bring down the full ACME Inc. local development environment using
`scripts/docker-manage.sh`.

Every volume this project declares — including `acme-mongodb-data` — is local
development data. The db-init jobs and Flyway migrations recreate the schema and
seed data on the next `start-local-dev`, so removing them costs a slower startup,
not real work.

## Workflow

1. **Resolve the project root.** Use the current working directory if it is the
   repo root; otherwise locate it with `git rev-parse --show-toplevel`.

2. **Ask whether to discard the data**, unless the user already said. This is the
   only decision in the skill, and one of the two answers is destructive:

   - *Stop and keep data* — faster restart, volumes survive.
   - *Stop and remove volumes* — every container, the `acme-network` network, and
     all of this project's data volumes go.

   Show what would be removed before a destructive teardown:

   ```bash
   proj=$(basename "$(git rev-parse --show-toplevel)")
   docker volume ls --filter "label=com.docker.compose.project=$proj" \
     --format '{{.Name}}'
   ```

   The filter is Docker's own compose label, so it lists exactly this project's
   volumes — never another project's, and volumes added later are picked up
   automatically. `$proj` reproduces how compose derives the project name (the
   repo directory basename), since `docker-compose.yml` declares no explicit
   `name:`.

3. **Stop the stack.**

   Keeping data:

   ```bash
   zsh scripts/docker-manage.sh stop
   ```

   Discarding data:

   ```bash
   zsh scripts/docker-manage.sh stop -v
   ```

   Either form stops application services before infrastructure and removes the
   `acme-network` network. `-v` additionally removes the data volumes.

4. **Confirm teardown.** Report what was stopped and, when `-v` was used, which
   volumes went. Verify nothing was left behind:

   ```bash
   docker ps -a --format '{{.Names}}' | grep '^acme-' || echo "(no containers)"
   docker volume ls --format '{{.Name}}' | grep '^acme-' || echo "(no volumes)"
   ```

   `k3d-acme-2026-*` containers belong to the k3d cluster, not this compose
   stack — leave them alone.

## Timeouts

- `stop` / `stop -v`: 120 000 ms

## Guardrails

- Always use `zsh` to invoke the script — it uses zsh-specific syntax.
- `-v` permanently deletes data. Confirm before using it, and never add it to a
  teardown the user asked to be non-destructive.
- Never run `docker volume prune` or `docker system prune` here. They reach
  beyond this compose project and would take other projects' volumes with them.
  Scope teardown to the project via the script or compose labels.
- Do not edit the script or compose files as part of this skill — only invoke it.
- If any step exits non-zero, surface the error output immediately; do not
  proceed to the next step.
- This skill targets Docker. For Podman-based stacks, use
  `scripts/podman-manage.sh` with the same command names, and substitute
  `podman volume` for `docker volume`.
