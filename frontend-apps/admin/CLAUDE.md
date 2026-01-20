# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Project Overview

Admin web application for the ACME Inc. e-commerce platform built with React 19 and TanStack Start.

## Node.js Version

This project uses nvm to manage Node.js versions. The required version is specified in `.nvmrc`.

**Important:** Always run `nvm use` before any `npm` command to ensure the correct Node.js version is active:

```bash
nvm use && npm run dev
nvm use && npm run test
nvm use && npm install
```

## Development Commands

```bash
npm run dev      # Start development server on port 3000
npm run build    # Build for production
npm run test     # Run Vitest tests
npm run preview  # Preview production build
```

## File Structure

- `src/routes/` - File-based routing (TanStack Router)
- `src/components/` - React components

## Testing

Unit tests use Vitest with React Testing Library:

```bash
npm run test
```

Test files are co-located with source files using `.test.ts` or `.test.tsx` suffix.
