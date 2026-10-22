# ADR-0052: Frontend Stack and State Management

## Status

Accepted

Relates to [ADR-0025](0025-multi-step-wizard-pattern.md) (Zustand and Zod for the profile
wizard) and [ADR-0030](0030-signin-form-reusable-patterns.md) (React Hook Form, Zod and the
Zustand auth store for sign-in).

## Context

There are two frontend apps, `frontend-apps/customer` and `frontend-apps/admin`.
`IMPLEMENTATION.md` → "Frontend applications" lists the intended stack, but no ADR records
it, or the rule the customer app has settled on for where state lives.

The customer app first kept server data in Zustand or component state. The profile and
account features fetch in `useEffect`: `customer.store.ts` loads and
persists the profile (`fetchProfile`, `persist` as `customer-storage`), and
`routes/preferences.tsx`, `routes/devices.tsx` and `ProfileCompletenessWidget` keep their
responses in `useState`. Product search (PIN-132) brought TanStack Query, and the catalog
and cart features since then load all server data through it.

## Decision

**Stack.** Both apps are built on what `package.json` declares:

- TanStack Start (`@tanstack/react-start`) with TanStack Router file-based routes
  (`@tanstack/router-plugin`, `routeTree.gen.ts`), on Vite 8 and Nitro.
- React 19 (`^19.3.0`) and TypeScript 6.
- Tailwind CSS 4 through `@tailwindcss/vite`.
- Vitest 4 with Testing Library and jsdom, and ESLint 9.

The customer app also uses:

- shadcn/ui (`components.json`, `new-york` style, lucide icons) built on Radix
  primitives, in `src/components/ui`.
- TanStack Query 5 for server data, with one `QueryClient` made in `routes/__root.tsx`.
- Zod 4 schemas in `src/schemas`, with React Hook Form 7 and `@hookform/resolvers`.
- Zustand 5 stores in `src/stores/*.store.ts`.

**State rule (customer app).** Server data lives in TanStack Query. Zustand holds only
state the client owns.

- One query key per resource, shared by every reader. `["cart"]` (`CART_QUERY_KEY` in
  `hooks/useCart.ts`) is the only cart state. The header `CartBadge` and the `/cart` page
  both read it, and `writeCart` cancels in-flight reads, then `setQueryData`s each mutation
  response, so the two update together without a refetch. `["availability", variantId]`
  (`availabilityQueryOptions` in `hooks/useVariantSelection.ts`) is shared by the product
  page and the cart page, so they share a cache.
- Zustand stores hold what no endpoint returns: `profileWizard.store.ts` (wizard steps and
  draft data, ADR-0025), `cartNotice.store.ts` (a one-off banner message, not persisted),
  and `auth.store.ts` (the signed-in user, persisted, ADR-0030).

**The admin app** is still the TanStack Start starter: `routes/index.tsx` and
`routes/demo/*`. It has no TanStack Query, Zustand, Zod, React Hook Form or shadcn/ui. It
takes these on when it gets real features.

## Consequences

### Positive

- Readers of the same resource can't disagree: there is one cache entry and writes go
  into it.
- Loading, error, cancellation and refetch come from TanStack Query rather than being
  written per screen.
- Zustand stores stay small and synchronous, and are easy to unit-test
  (`*.store.test.ts`).

### Negative

- The rule is not applied everywhere. `customer.store.ts` still fetches and persists the
  profile in Zustand, and the preferences, devices and profile completeness screens fetch
  in `useEffect`. These are older patterns, not examples to copy.
- `auth.store.ts` persists the signed-in user to `localStorage`, so it can disagree with
  the server session until something refreshes it.
- The two apps share no package. Versions are kept in step by hand.
- `IMPLEMENTATION.md` lists TanStack Table and Virtual and Mock Service Worker, but neither
  `package.json` declares them. It also says React 19.2, while both apps declare
  `^19.3.0`.

### Mitigations

- New server data in the customer app goes through a `useQuery` hook in `src/hooks` with a
  shared key. Moving the profile and preferences screens onto TanStack Query is follow-up
  work.
- `useLogout` clears `auth.store` and `customer.store` on sign-out. Sign-out also resets
  `["cart"]`, so the badge falls back to the guest cart (`ARCHITECTURE.md`, Shopping Cart
  Service).
