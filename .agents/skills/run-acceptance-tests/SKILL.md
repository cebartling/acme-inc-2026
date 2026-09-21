---
name: run-acceptance-tests
description: Run the project's acceptance test suite via scripts/run-acceptance-tests.sh. Use when the user asks to "run acceptance tests", "run the acceptance suite", "run cucumber tests", invokes /run-acceptance-tests, or wants to execute the Cucumber.js + Playwright end-to-end tests for the ACME Inc. monorepo.
---

# run-acceptance-tests

Run the repository's acceptance test suite by invoking `scripts/run-acceptance-tests.sh` from the project root.

## Prerequisites

- Node.js 24+ (LTS/Krypton) is required. The script handles nvm/fnm version switching automatically.

## Workflow

1. Resolve the project root. The script lives at `<repo>/scripts/run-acceptance-tests.sh`. Use the current working directory if it is the repo root; otherwise locate it with `git rev-parse --show-toplevel`.

### 1a. Ensure application services are running

The ACME application stack consists of four services:

| Service | Default health endpoint |
|---|---|
| Identity Service | `http://localhost:${IDENTITY_SERVICE_PORT:-10300}/actuator/health` |
| Customer Service | `http://localhost:${CUSTOMER_SERVICE_PORT:-10301}/actuator/health` |
| Notification Service | `http://localhost:${NOTIFICATION_SERVICE_PORT:-10302}/actuator/health` |
| Customer Frontend | `http://localhost:${CUSTOMER_FRONTEND_PORT:-7600}/` |

Check each one before running tests:

```bash
curl -sf http://localhost:${IDENTITY_SERVICE_PORT:-10300}/actuator/health   && \
curl -sf http://localhost:${CUSTOMER_SERVICE_PORT:-10301}/actuator/health   && \
curl -sf http://localhost:${NOTIFICATION_SERVICE_PORT:-10302}/actuator/health && \
curl -sf http://localhost:${CUSTOMER_FRONTEND_PORT:-7600}/
```

If **any** check fails, start all services automatically:

```bash
zsh scripts/docker-manage.sh start
```

Then wait up to 120 seconds for **all four** services to become healthy, polling every 5 seconds. Check all four endpoints each poll cycle; only proceed once every one responds successfully. If any service is still unhealthy after 120 seconds, stop and report which service(s) failed — do not run the tests.

2. Pass through any user-supplied flags verbatim. The script accepts:

   **Test selection:**
   - `--smoke` — `@smoke` tagged scenarios only
   - `--regression` — `@regression` tagged scenarios
   - `--customer` — customer feature files only
   - `--admin` — admin feature files only
   - `--api` — `@api` tagged scenarios only (no browser)

   **Execution:**
   - `--headed` — visible browser (not headless)
   - `--skip-install` — skip `npm ci`
   - `--no-open` — don't auto-open the HTML report
   - `--quiet | -q` — progress-bar only output

   Any unrecognised argument is forwarded to Cucumber.js as-is.

   If the user did not specify flags, run the script with `--no-open` to avoid popping a browser tab automatically (unless the user explicitly requests the report to open).

3. Invoke with zsh (the script uses zsh-specific features):

   ```bash
   zsh scripts/run-acceptance-tests.sh [user-supplied flags]
   ```

   Use the Bash tool with a generous timeout — acceptance tests with Playwright can take several minutes. 600000ms (10 min) is the safe default; smoke-only runs can use 180000ms.

4. Report the outcome:
   - Exit code (0 = all passed, non-zero = failures)
   - Scenario counts (passed / failed / skipped / pending) from the Cucumber output
   - If any scenarios failed, surface their names and the first error or step failure
   - HTML report path: `acceptance-tests/reports/cucumber-report.html`
   - JSON report path: `acceptance-tests/reports/cucumber-report.json`

## Notes

- `@rate-limiting` and `@wip` tags are excluded by default in the script — don't add them unless the user asks.
- API-only tests (`@api`) do not launch a browser; UI tests (`@customer`, `@admin`) do.
- The script auto-opens the HTML report in the default browser unless `--no-open` is passed.

## Guardrails

- Do not edit the script, step definitions, or feature files as part of this skill — only run it.
- Starting services automatically (step 1a) is allowed; stopping them is not — leave services running after the tests complete.
- If the script exits non-zero, report the failures clearly but do not attempt to fix them automatically without the user's direction.
