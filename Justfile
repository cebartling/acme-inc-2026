set shell := ["zsh", "-cu"]

# Default target lists available commands.
default:
    @just --list

# Start all infrastructure + application services for demos.
demo-up:
    ./scripts/docker-manage.sh start

# Stop all services started for demos.
demo-down:
    ./scripts/docker-manage.sh stop

# Live headed walkthrough of the sign-in flow.
demo-signin:
    cd demos && bun run src/run.ts signin

# Recorded sign-in walkthrough; .webm lands in demos/recordings/.
demo-signin-record:
    cd demos && bun run src/run.ts signin --mode=record

# Live headed walkthrough of the registration flow.
demo-register:
    cd demos && bun run src/run.ts register

# Recorded registration walkthrough; .webm lands in demos/recordings/.
demo-register-record:
    cd demos && bun run src/run.ts register --mode=record

# Live headed walkthrough of the logout flow (DEMO_LOGOUT_ALL=true to demo all-devices).
demo-logout:
    cd demos && bun run src/run.ts logout

# Recorded logout walkthrough; .webm lands in demos/recordings/.
demo-logout-record:
    cd demos && bun run src/run.ts logout --mode=record

# Live headed walkthrough of the invalid-credentials error UX (US-0003-10).
demo-invalid-credentials:
    cd demos && bun run src/run.ts invalid-credentials

# Recorded invalid-credentials walkthrough; .webm lands in demos/recordings/.
demo-invalid-credentials-record:
    cd demos && bun run src/run.ts invalid-credentials --mode=record

# Live headed walkthrough of inactive-account handling (US-0003-11):
# PENDING_VERIFICATION, SUSPENDED, DEACTIVATED card variants + reactivation.
demo-inactive-account:
    cd demos && bun run src/run.ts inactive-account

# Recorded inactive-account walkthrough; .webm lands in demos/recordings/.
demo-inactive-account-record:
    cd demos && bun run src/run.ts inactive-account --mode=record

# Live headed walkthrough of token-refresh + OWASP reuse-detection (US-0003-12).
# Drives the /api/v1/auth/refresh endpoint from inside the customer-app
# browser context; shows tokenFamily rotation on the happy path and
# TOKEN_REUSE_DETECTED on replay of an old refresh cookie.
demo-token-refresh:
    cd demos && bun run src/run.ts token-refresh

# Recorded token-refresh walkthrough; .webm lands in demos/recordings/.
demo-token-refresh-record:
    cd demos && bun run src/run.ts token-refresh --mode=record

# Live headed walkthrough of product search (US-0004-01):
# search, sort, spelling suggestion, and clear input.
demo-product-search:
    cd demos && bun run src/run.ts product-search

# Recorded product-search walkthrough; .webm lands in demos/recordings/.
demo-product-search-record:
    cd demos && bun run src/run.ts product-search --mode=record

# Run all demos sequentially in live mode.
demo-all: demo-register demo-signin demo-logout

# Run all demos sequentially in record mode.
demo-all-record: demo-register-record demo-signin-record demo-logout-record
