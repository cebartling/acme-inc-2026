# ADR-0053: Product Search on PostgreSQL Full-Text Search

## Status

Accepted

## Context

Epic 002 and US-0004-01 describe a separate Search Service, fed by product catalog events
for reindexing (`epics/002-product-catalog.md`). The Epic 004 diagram
(`user-stories/0004-customer-shopping-experience/README.md`) gives it an Elasticsearch
index. US-0004-01 asks for full-text search with
category facets, US-0004-02 for autocomplete, and US-0004-03 for filters.

Search was built inside the product service when that service was created (PIN-132,
commit `98d94aa`: "PostgreSQL FTS"). Autocomplete (PIN-135) and filtering (PIN-138) were
added on top. Products already live in the service's own PostgreSQL database
`acme_products` ([ADR-0041](0041-per-service-persistence-model.md)).

## Decision

Product search runs on PostgreSQL in `backend-services/product`. There is no search engine,
search index or separate search service.

1. **A generated, weighted `tsvector`.** `V1__create_products.sql` defines
   `products.search_vector` as `GENERATED ALWAYS AS (...) STORED` over the `english`
   configuration: `name` at weight A, `description` at B and `category` at C. A GIN index
   (`products_search_vector_idx`) backs it. PostgreSQL keeps it current on every write, so
   there is nothing to reindex.
2. **Matching and ranking.** The native queries in `ProductRepository` match with
   `search_vector @@ plainto_tsquery('english', :query)`. `searchByRelevance` sorts by
   `ts_rank` and then `id`; the other sorts are price and `created_at`. Only `PUBLISHED`
   products match. A `*Filtered` variant of each sort adds category and price-range
   predicates, and `SearchProductsUseCase` picks one of the eight queries.
3. **Facets** come from a `GROUP BY category` over the same match (`getCategoryFacets`),
   with the price filter but not the category filter. If it fails, search answers with
   empty facets.
4. **Spelling suggestions use `pg_trgm`.** The extension is created in `V1`. When a search
   finds nothing, `findSpellingSuggestion` returns the published name with the highest
   `similarity(name, :query)` above 0.1.
5. **Autocomplete is a prefix `ILIKE`, not full text.** `autocompleteProducts` and
   `autocompleteCategories` match `name ILIKE :prefix || '%'`, ordering products by
   `similarity`. `AutocompleteUseCase.escapeLikePattern` escapes `\`, `%` and `_`. The
   `products_name_trgm_idx` GIN index (`gin_trgm_ops`) is on `name`.
6. **Category browsing stays off the search path.** `findDistinctCategories` and
   `findByCategory` never touch `search_vector`, so the customer app can fall back to them
   when search fails ([ADR-0051](0051-client-side-search-circuit-breaker.md)).

## Consequences

### Positive

- No search cluster to run, and no index to keep in sync with the catalog. Search sees a
  product change as soon as it is committed.
- Search, facets and stock come from one database, so a result can't show a product the
  catalog has archived.
- Weights, ranking and filters are plain SQL in one repository.

### Negative

- Ranking is basic. `plainto_tsquery` ANDs the words, with English stemming and no
  operators. There is no fuzzy matching or synonyms, and no result highlighting, which
  Epic 002 F6 lists.
- `tags` is a column on `products` but is not part of `search_vector`.
- Search load lands on the catalog database, and a slow search query slows the same
  database that category browsing and the cart's pricing calls read.
- The sort × filter combinations are eight near-identical native queries. A change to the
  match condition has to be made in each, and in the two count queries and the facet query.
- Changing the weights or text configuration means a migration that rewrites the
  generated column.
- No repository integration test runs the full-text queries against PostgreSQL. The
  `SearchProductsUseCaseTest` and `AutocompleteUseCaseTest` tests mock the repository.
- US-0004-01 mentions Caffeine caching for popular queries. It is not built.

### Mitigations

- The customer app guards search with a 2s deadline and a circuit breaker (ADR-0051), so a
  slow or failing search degrades to category browsing.
- The acceptance features `product-search-ui.feature` (7 scenarios) and
  `search-autocomplete-ui.feature` run real queries end to end against the service.
- A dedicated search engine needs its own ADR, saying how it is fed and reindexed.
