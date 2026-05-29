# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Project Overview

Customer-facing web application for the ACME Inc. e-commerce platform built with React 19 and TanStack Start.

## Node.js Version

This project uses nvm to manage Node.js versions. The required version is specified in `.nvmrc`.

**Important:** Always run `nvm use` before any `npm` command to ensure the correct Node.js version is active:

```bash
nvm use && npm run dev
nvm use && npm run test
nvm use && npm install
```

## Development Commands

### Development Tasks Script

Use `scripts/dev-tasks.sh` for common development tasks:

```bash
# Run all tasks (routes, lint, format)
./scripts/dev-tasks.sh

# Individual commands
./scripts/dev-tasks.sh routes    # Generate TanStack Router route tree
./scripts/dev-tasks.sh lint      # Run ESLint with auto-fix
./scripts/dev-tasks.sh format    # Run Prettier with auto-fix
./scripts/dev-tasks.sh check     # Run all checks without modifications
./scripts/dev-tasks.sh fix       # Run all fixes (default)
```

### npm Scripts

```bash
npm run dev      # Start development server on port 3000
npm run build    # Build for production
npm run test     # Run Vitest tests
npm run preview  # Preview production build
```

## Code Quality

- **ESLint**: Configured in `eslint.config.js` (ESLint 9 flat config)
- **Prettier**: Available via node_modules
- **TypeScript**: Strict mode enabled

## Pre-commit Hook (Husky + lint-staged)

Prettier formatting is enforced on every commit via Husky + lint-staged. The hook runs automatically after `npm install` (via the `prepare` script).

**Important — single owner of `core.hooksPath`:** The `prepare` script does `cd ../.. && husky frontend-apps/customer/.husky`, which sets the repo-level `git config core.hooksPath` to this package's `.husky/_` directory. Because `core.hooksPath` is a single git config key, **only one package in this monorepo should own it**. If a second frontend package ever needs its own pre-commit hook, consolidate both into a single root-level Husky setup rather than letting two `prepare` scripts overwrite the same key.

## File Structure

- `src/routes/` - File-based routing (TanStack Router)
- `src/components/` - React components
- `src/schemas/` - Zod validation schemas
- `src/stores/` - Zustand state stores
- `src/routeTree.gen.ts` - Auto-generated route tree (do not edit manually)

## Testing

Unit tests use Vitest with React Testing Library:

```bash
npm run test
```

Test files are co-located with source files using `.test.ts` or `.test.tsx` suffix.
