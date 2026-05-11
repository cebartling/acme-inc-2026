# ACME Demos

Bun + Playwright scripted walkthroughs of the ACME UIs, runnable as live
headed demos or as recorded `.webm` artifacts.

Demos are **not** tests — they have no assertions and never fail a CI
build. Their job is to take the audience through a real flow at a
human-readable pace. Pass/fail coverage lives in `../acceptance-tests/`.

## Prerequisites

| Tool         | Version | Install                                                    |
| ------------ | ------- | ---------------------------------------------------------- |
| Bun          | 1.3+    | `curl -fsSL https://bun.sh/install \| bash`                |
| just         | 1.20+   | `brew install just`                                        |
| Chromium     | bundled | Installed once via `bunx playwright install chromium`      |
| Backend + UI | running | `just demo-up` (wraps `./scripts/docker-manage.sh start`)  |

First-time setup:

```bash
cd demos
bun install
bunx playwright install chromium
```

## Running

All demos are exposed as Justfile targets at the repo root:

```bash
just demo-up                 # Start backend services + customer frontend
just demo-register           # Live headed walkthrough of registration
just demo-register-record    # Same flow, saved to demos/recordings/*.webm
just demo-signin             # Live headed walkthrough of sign-in
just demo-signin-record      # Same flow, recorded
just demo-logout             # Live headed walkthrough of logout
just demo-logout-record      # Same flow, recorded
just demo-all                # Sequence: register → signin → logout (live)
just demo-all-record         # Sequence: register → signin → logout (recorded)
just demo-down               # Stop services when finished
```

Recordings land in `demos/recordings/<name>-<timestamp>.webm`. The
directory is gitignored.

## Configuration

Behavior is controlled by env vars (all optional):

| Var                 | Default                  | Purpose                                     |
| ------------------- | ------------------------ | ------------------------------------------- |
| `CUSTOMER_APP_URL`  | `http://localhost:7600`  | Where the customer frontend is reachable    |
| `DEMO_EMAIL`        | `demo@acme.test`         | Account used by the sign-in demo            |
| `DEMO_PASSWORD`     | `DemoPass123!`           | Password for the sign-in demo               |
| `DEMO_SLOWMO_MS`    | `250`                    | Playwright `slowMo` — increase for live use |
| `DEMO_LOGOUT_ALL`   | `false`                  | Set `true` to exercise "Sign Out All Devices" (with confirm dialog) instead of single-session logout |

### Sign-in credentials

The `signin` demo prefers the most recently registered account: each
`just demo-register` run writes the new email/password to
`demos/.last-registered.json` (gitignored), and `signin` reads it on
startup. This makes `just demo-all` (= register → signin) a one-shot
happy-path demo.

If no such file exists, `signin` falls back to `DEMO_EMAIL` /
`DEMO_PASSWORD` from the environment — useful when running `signin`
standalone against a pre-seeded account.

## Adding a new demo

1. Drop `demos/src/scenarios/<name>.ts` exporting a default function
   `(page) => Promise<void>`.
2. Add two Justfile targets to the repo root:
   ```just
   demo-<name>:        cd demos && bun run src/run.ts <name>
   demo-<name>-record: cd demos && bun run src/run.ts <name> --mode=record
   ```
3. Cross-reference selectors in `../acceptance-tests/pages/customer/` —
   they are the canonical source for which locators map to which UI
   elements. Demos intentionally do not import from acceptance-tests
   (different runtime, different package manager).

## Why a separate package?

- **Different runtime.** Acceptance tests use npm + Node + Cucumber.
  Demos use Bun and plain Playwright — no Cucumber, no World, no Page
  Object base class. The runtime choice was a requirement of
  [PIN-116](https://linear.app/pintail-consulting/issue/PIN-116).
- **Different intent.** Tests must be deterministic and fast. Demos
  must be readable to a human watching them.
- **Different lifecycle.** Demos can break when the UI changes without
  blocking releases; tests cannot.
