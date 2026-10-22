# ADR-0051: Client-Side Circuit Breaker for Product Search

## Status

Accepted

## Context

US-0004-09 (PIN-265) asks that a search outage must not block a customer from finding
products. The story was written against a standalone Search Service and Product Catalog
Service. Both turned out to be the product service (`backend-services/product`), but on
separate code paths: search queries `products.search_vector` with full-text operators
([ADR-0053](0053-search-on-postgresql-full-text.md)), while category browsing reads
`products` directly (`ProductRepository.findDistinctCategories`, `findByCategory`). So
category browsing can stay up when search fails.

The only other circuit breaker in the platform is on the backend. The notification
service guards its customer lookup
([ADR-0024](0024-synchronous-customer-lookup-for-notifications.md)) with Resilience4j (`ResilienceConfig`): a count-based window of 10 calls, a 50% failure
or slow-call rate, 30s open, three half-open calls, exported through Micrometer. Search is
called straight from the browser, so no backend breaker sits on its path.

## Decision

The customer app guards product search with its own small breaker,
`frontend-apps/customer/src/lib/searchCircuitBreaker.ts`, and falls back to category
browsing when search fails.

1. **`CircuitBreaker` is a generic class with no library behind it.** It moves between
   `CLOSED`, `OPEN` and `HALF_OPEN`. `OPEN` rejects with `CircuitOpenError` without
   touching the network. `OPEN` turns into `HALF_OPEN` by elapsed time, read in
   `getState()`, not by a timer. The clock is injectable for tests.
2. **Thresholds**: 5 consecutive failures open it (`threshold`), and a probe is allowed
   30s later (`resetTimeoutMs`). A success closes it and resets the count. A failed probe
   reopens it at once.
3. **Probes are single-flight.** Concurrent calls in `HALF_OPEN` wait on the one probe in
   flight instead of each sending their own.
4. **Only an unhealthy service counts.** `execute` takes a failure predicate.
   `productApi.search` in `services/api.ts` passes `isServiceUnhealthy`: an `ApiError`
   with status 0 (a timeout) or 5xx, or any non-`ApiError` rejection from `fetch` (DNS,
   connection refused, CORS). A 4xx is rethrown without touching the breaker.
5. **A 2s deadline** (`SEARCH_TIMEOUT_MS`) is passed to `apiRequest` as `timeoutMs`, which
   aborts the request and throws `TimeoutError` (an `ApiError` with status 0).
6. **`execute` rethrows; it never returns a fallback value.** The fallback is a different
   component tree from different endpoints, so callers catch and decide what to render.
7. **The guarded query sets `retry: false`.** `useSearchWithFallback` turns off TanStack
   Query's default `retry: 3`, which would spend four requests per search and make
   "5 consecutive failures" mean twenty requests.
8. **The fallback renders from the first failure**, not the fifth. `isSearchUnavailable`
   follows the query's `isError`. `routes/search.tsx` then shows `SearchUnavailableBanner`
   with a retry control and `CategoryFallbackBrowse`, which calls `GET /api/v1/categories`
   and `GET /api/v1/categories/{name}/products` without the breaker.
9. **State is one module-scoped instance per tab, in memory.** `searchCircuitBreaker` is
   shared by every caller in the tab and resets on a full page reload.

`IMPLEMENTATION.md` makes this the pattern for any call the UI can offer a fallback for:
a separate `CircuitBreaker` instance per dependency, a predicate, `timeoutMs`,
`retry: false`, and an explicit refetch control.

## Consequences

### Positive

- A search outage costs the customer search, not product discovery. After five failures
  the app stops calling a service it knows is down.
- 4xx responses never push the customer into the fallback.
- The breaker has no dependencies and is unit-tested with a fake clock
  (`searchCircuitBreaker.test.ts`).

### Negative

- Breaker state is per tab. Each tab and each reload learns about an outage on its own,
  and the service sees no coordinated back-off across customers.
- Nothing is exported. The state is in the browser and the frontend has no telemetry
  transport, so AC-0004-09-08 (Prometheus metrics) is deferred to PIN-270.
- It is a second breaker with different semantics from ADR-0024's: consecutive failures
  here, a failure-rate window there.
- Only `productApi.search` is guarded. `productApi.autocomplete` hits the same service with
  no breaker and no deadline.
- Anything that slows a search counts as search failing. The search request waits on the
  `SearchExecuted` Kafka publish, so a slow broker can open the breaker
  (`ARCHITECTURE.md`, Product Service).
- TanStack Query serves a cached failure for an unchanged query key, so re-submitting the
  same term does not send a probe without the banner's retry control.

### Mitigations

- `SearchUnavailableBanner` calls `refetch()`, which reaches the breaker and sends a probe
  once the 30s window has passed.
- Acceptance tests that must reach the threshold navigate client-side, not with repeated
  `page.goto`, since a reload resets the breaker (`IMPLEMENTATION.md`).
- `ARCHITECTURE.md` → "Client-Side Resilience (Circuit Breaker)" describes the state
  machine and links the reasoning recorded here.
