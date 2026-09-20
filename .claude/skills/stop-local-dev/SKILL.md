---
name: stop-local-dev
description: Stop the full local development stack via scripts/docker-manage.sh, removing the Docker network and data volumes except acme-mongodb-data. Use when the user asks to "stop local dev", "tear down the dev stack", "shut down the environment", or invokes /stop-local-dev.
---

# stop-local-dev

Bring down the full ACME Inc. local development environment using
`scripts/docker-manage.sh`, removing the Docker network and every data volume
**except `acme-mongodb-data`**.

## Why this skill does not use `down -v`

`acme-mongodb-data` is shared with another project and must never be removed.

`docker compose down -v` removes every named volume the compose file declares,
with no way to exclude one — and `docker-compose.yml` declares `mongodb_data`
with the name `acme-mongodb-data` without marking it `external`, so compose
treats it as owned by this project. `infra-down -v` would therefore destroy it.

Instead: stop infra *without* `-v`, then remove the volumes explicitly,
filtering that one out.

## Workflow

1. **Resolve the project root.** Use the current working directory if it is the
   repo root; otherwise locate it with `git rev-parse --show-toplevel`.

2. **Stop application services.** Run:
   ```bash
   zsh scripts/docker-manage.sh apps-down
   ```
   This stops and removes application containers (identity, customer,
   notification services, frontend, db-init jobs). Safe to run even if apps are
   already down.

3. **Stop infrastructure services and remove the network.** Run — **without
   `-v`**:
   ```bash
   zsh scripts/docker-manage.sh infra-down
   ```
   Compose removes the `acme-network` network as part of infra teardown. Volumes
   are left in place by this step.

4. **Show which volumes will be removed, and get confirmation.** This is the
   destructive step, so print the list first and wait for an explicit go-ahead:
   ```bash
   proj=$(basename "$(git rev-parse --show-toplevel)")
   docker volume ls --filter "label=com.docker.compose.project=$proj" \
     --format '{{.Name}}' | grep -vx 'acme-mongodb-data'
   ```
   The list is derived from Docker's own compose labels rather than hardcoded,
   so volumes added later are picked up automatically, and volumes belonging to
   *other* compose projects are never included. `$proj` reproduces how compose
   derives the project name (the repo directory basename), since
   `docker-compose.yml` declares no explicit `name:`.

   **Check the output does not contain `acme-mongodb-data` before continuing.**
   If it does, stop and investigate — do not proceed.

5. **Remove those volumes.** Only after the user confirms:
   ```bash
   proj=$(basename "$(git rev-parse --show-toplevel)")
   docker volume ls --filter "label=com.docker.compose.project=$proj" \
     --format '{{.Name}}' | grep -vx 'acme-mongodb-data' \
     | xargs -r docker volume rm
   ```
   "volume is in use" means a container survived step 2 or 3 — investigate
   rather than forcing it.

6. **Confirm teardown.** Report what was stopped, which volumes were removed,
   and state explicitly that `acme-mongodb-data` was preserved. Verify it
   survived — this must print the name:
   ```bash
   docker volume ls --format '{{.Name}}' | grep -x 'acme-mongodb-data'
   ```

## Timeouts

- `apps-down`: 60 000 ms
- `infra-down`: 60 000 ms
- `docker volume rm`: 60 000 ms

## Guardrails

- **Never remove `acme-mongodb-data`.** It belongs to another project. Never run
  `infra-down -v`, `docker compose down -v`, or `docker volume prune` in this
  repo — none of them can spare that volume.
- Always pipe through `grep -vx 'acme-mongodb-data'` before any `docker volume
  rm`, even when the list looks obviously safe.
- Always use `zsh` to invoke the script — it uses zsh-specific syntax.
- Always stop apps before infra — stopping infra first while apps are running can
  leave orphaned containers.
- Step 5 permanently deletes data. Always run step 4 and get confirmation first.
- If the user wants to stop the stack *without* discarding data, run only steps 2
  and 3 and skip the volume removal entirely.
- Do not edit the script or compose files as part of this skill — only invoke it.
- If any step exits non-zero, surface the error output immediately; do not
  proceed to the next step.
- This skill targets Docker. For Podman-based stacks, use
  `scripts/podman-manage.sh` with the same command names (`apps-down`,
  `infra-down`), and substitute `podman volume` for `docker volume`.
