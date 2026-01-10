# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Project Overview

Customer-facing web application for the ACME Inc. e-commerce platform built with React 19 and TanStack Start.

## Node.js Version

This project uses nvm to manage Node.js versions. The required version is specified in `.nvmrc`:

```bash
nvm use
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
