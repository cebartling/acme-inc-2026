---
name: run-unit-tests
description: Run the project's unit test suite via scripts/run-unit-tests.sh. Use when the user asks to "run unit tests", "run the tests", invokes /run-unit-tests, or wants to execute the backend (identity, customer, notification) and/or frontend (admin, customer) unit test suites for the ACME Inc. monorepo.
---

# run-unit-tests

Run the repository's unit test suite by invoking `scripts/run-unit-tests.sh` from the project root.

## Workflow

1. Resolve the project root. The script lives at `<repo>/scripts/run-unit-tests.sh`. Use the current working directory if it is the repo root; otherwise locate the repo root with `git rev-parse --show-toplevel`.

2. Pass through any user-supplied flags verbatim. The script accepts:

   - Selection: `--all`, `--backend`, `--frontend`, `--identity`, `--customer`, `--notification`, `--admin`, `--customer-app`
   - Execution: `--parallel`, `--skip-install`, `--verbose|-v`, `--quiet|-q`
   - `--help|-h`

   If the user did not specify flags, run the script with no arguments (it defaults to all suites, sequential).

3. Invoke with zsh (the script's shebang is `#!/usr/bin/env zsh` and it uses zsh-only features):

   ```bash
   zsh scripts/run-unit-tests.sh [user-supplied flags]
   ```

   Use the Bash tool. Pick a timeout generous enough for a clean Gradle + npm test cycle — 600000ms (10 min) is a safe default; for `--parallel` or single-suite runs you can go lower.

4. Report the outcome:
   - The exit code (0 = all passed, 1 = failures, 2 = bad args)
   - The summary table the script prints (suites, passed/failed/skipped totals)
   - If any suite failed, surface the failing suite name(s) and point the user at the first failing test or stack trace from the output.

## Guardrails

- Do not edit the script, .env files, or test sources as part of this skill — only run it.
- Do not pass `--no-verify`-style or test-skipping flags; the script does not accept them and the user has not requested them.
- If the script reports a SKIPPED suite because a directory or dependency is missing, report it; don't try to "fix" the project layout from inside this skill.
