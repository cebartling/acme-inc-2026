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

# Run all demos sequentially in live mode.
demo-all: demo-register demo-signin

# Run all demos sequentially in record mode.
demo-all-record: demo-register-record demo-signin-record
