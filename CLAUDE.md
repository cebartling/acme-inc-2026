# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Demo e-commerce platform for the fictitious ACME, Inc. company.

## Current State

This is a new repository. Build commands, architecture documentation, and development workflows will be added as the codebase evolves.

## Prerequisites

### Shell

This repository requires **zsh** for running shell scripts in the `scripts/` directory. The scripts use zsh-specific features and syntax.

- **macOS**: zsh is the default shell since macOS Catalina (10.15)
- **Linux**: Install via package manager (e.g., `apt install zsh` or `dnf install zsh`)

Verify zsh is available:
```bash
zsh --version
```

## MCP Servers

### Context7

Use the Context7 MCP server to retrieve up-to-date documentation and code examples for any programming library or framework.

**Tools:**
- `resolve-library-id` - Resolves a package/product name to a Context7-compatible library ID. Call this first before querying docs.
- `query-docs` - Retrieves documentation and code examples using the library ID obtained from resolve-library-id.

**Usage:**
1. First call `resolve-library-id` with the library name to get the Context7 library ID
2. Then call `query-docs` with the library ID and your specific question

### Playwright

Use the Playwright MCP server for browser automation, testing, and web interaction.

**Tools:**
- `browser_navigate` - Navigate to a URL
- `browser_snapshot` - Capture accessibility snapshot of the current page (preferred over screenshots for actions)
- `browser_click` - Click on elements
- `browser_type` - Type text into editable elements
- `browser_fill_form` - Fill multiple form fields at once
- `browser_take_screenshot` - Take a screenshot of the current page
- `browser_evaluate` - Evaluate JavaScript on the page
- `browser_wait_for` - Wait for text to appear/disappear or a specified time
- `browser_tabs` - List, create, close, or select browser tabs
- `browser_close` - Close the page
- `browser_console_messages` - Returns all console messages
- `browser_network_requests` - Returns all network requests since page load

**Usage:**
1. Navigate to a URL with `browser_navigate`
2. Use `browser_snapshot` to get an accessibility tree of the page (better than screenshots for interactions)
3. Interact with elements using their ref from the snapshot
4. Use `browser_wait_for` to wait for dynamic content

## Acceptance Tests

The acceptance tests are located in `acceptance-tests/` and use Cucumber.js with Playwright.

### Test Types and Tags

| Tag | Description | Browser Required |
|-----|-------------|------------------|
| `@customer` | Customer frontend UI tests | Yes |
| `@admin` | Admin frontend UI tests | Yes |
| `@api` | API-only tests (HTTP calls) | No |
| `@registration` | Registration flow tests | Yes (navigates itself) |

### Hook Behavior

The test hooks in `support/hooks.ts` conditionally launch Playwright:

- **UI tests** (`@customer` or `@admin`): Launch browser, create context, navigate to app
- **API tests** (`@api` without `@customer`/`@admin`): Only initialize API clients, no browser

This optimization improves performance for API/backend tests.

### Running Tests

```bash
cd acceptance-tests

npm run test           # All tests
npm run test:smoke     # Smoke tests only
npm run test:customer  # Customer UI tests
npm run test:headed    # With visible browser
```
