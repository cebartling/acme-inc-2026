# ADR-0069: Product's Catalog Read Model Is Built from Table Capture, and Search Moves to MongoDB

## Status

Accepted

Part of [PIN-534](https://linear.app/pintail-consulting/issue/PIN-534/wire-debezium-cdc-into-cqrs-for-all-services),
built for product in PIN-550. Implements [ADR-0004](0004-change-data-capture.md) and
[ADR-0006](0006-mongodb-query-store.md) for product, as the exception ADR-0004 names: product captures
its tables directly instead of using an outbox. Supersedes
[ADR-0053](0053-search-on-postgresql-full-text.md) for the search engine; ADR-0053's decision to search
inside the product service stands.

## Context

Product has no write path. Its catalog comes from Flyway seed migrations, and all six read endpoints
(categories, products by category, product detail, autocomplete, prices, availability) plus search
queried PostgreSQL. Its three events (`ProductViewed`, `SearchExecuted`, `FiltersApplied`) are
analytics published from read paths, not state changes.

Without a write path there is nothing to write an outbox row from, so the outbox pattern of
[ADR-0065](0065-customer-read-model-from-snapshots.md) to [ADR-0068](0068-notification-outbox-and-deliveries-read-model.md)
does not apply. What does change is the catalog tables themselves, through migrations.

Search was the hard part. It used `tsvector`/`tsquery` full-text search and `pg_trgm` similarity.
MongoDB's text search works differently, and Amazon DocumentDB (the AWS demo, ADR-0064) supports less.

## Decision

1. **Debezium captures the four catalog tables directly.** The connector `product-catalog`
   (`docker/debezium/connectors/product-catalog.json`) publishes each table's row changes to
   `product.public.<table>`, flattened by `ExtractNewRecordState`. Its `snapshot.mode` is `initial`
   (the outbox connectors use `no_data`), so the first run loads the seeded catalog. A delete arrives
   with only the row's `id`: the tables use the default replica identity, so the other columns are
   blank. `acme_products` had no publication, so `product-db-init` creates `dbz_publication`.
2. **A projector mirrors the rows, then rebuilds a view.** `CatalogChangeProjector` writes each row to
   a per-table mirror collection in `acme_query` (`catalog_products`, `catalog_variants`,
   `catalog_variant_images`, `catalog_tier_pricing`). `CatalogViewRebuilder` then rebuilds that
   product's `catalog_product_view` document, with variants, images and tier pricing embedded and the
   stock and card image derived. Kafka orders messages only within a topic, so a variant can arrive
   before its product; a rebuild reads the mirrors as they are, so replays and any arrival order
   converge. A delete reads the owning product from the stored mirror row before removing it.
3. **The endpoints read the view.** `CatalogReadModelQueries` serves categories, products by category,
   detail, prices and availability, with the same responses as before. Stock and the card image come
   from each document, so the separate stock lookup is gone.
4. **Search uses a MongoDB text index.** `CatalogSearchQueries` indexes name, description and category
   with weights 10, 4 and 2 (`search_vector`'s A, B and C) and English stemming. Several words must all
   match, as `plainto_tsquery` required; MongoDB matches any word, so each word is quoted, which matches
   the exact word with no stemming. Punctuation in a query is dropped. The spelling suggestion and the
   autocomplete ranking need `pg_trgm`'s similarity, which MongoDB lacks; `TrigramSimilarity` computes
   it over the product names and is tested against PostgreSQL's values.
5. **The analytics events keep publishing directly.** They are not state changes, and losing one is
   acceptable. This is the documented exception ADR-0004 names.
6. **PostgreSQL is only the catalog's write side.** Flyway still owns the schema. The JPA entities,
   repositories and starter are removed; a Flyway migration test replaces what the JPA integration
   tests covered.

## Consequences

- **Relevance order differs from PostgreSQL's.** The same results come back, but `ts_rank` and MongoDB's
  text score order them differently after the first one or two. Counts, facets, filters, the price and
  newest sorts, the spelling suggestion and autocomplete were identical in a 72-request comparison; 11
  relevance-sorted requests differed only in order. No acceptance scenario checks that order.
- **A multi-word query loses stemming.** "widgets kit" does not match "widget kit". A single word is
  stemmed as before.
- **DocumentDB is not verified.** Its documentation lists weighted `$text` and `$meta` score sorting as
  supported but does not say whether it stems. Tested against MongoDB 7.0 only.
- **The reads are eventually consistent.** A catalog change shows once the projector has applied it:
  under a second locally. The cart's stock checks call the availability endpoint, so they now see a
  change that much later; they stay on the stock-check behaviour of PIN-328 to PIN-353.
- **After the retries, a message is skipped.** A message that fails five times (over about 31 seconds)
  is logged and skipped, so a MongoDB outage longer than that leaves those rows stale until each
  changes again or the connector's snapshot is re-run. An update that changes nothing does not
  re-emit the row. An invalid message is skipped at once, not retried.
- **One consumer applies every message.** The rebuild is a read then a write with nothing guarding it,
  which is safe with one consumer thread and one instance. A second would need the rows keyed by
  product, or a version check on the view write.
- **Autocomplete and the spelling suggestion read every published name.** Fine for this catalog; a large
  one would need a different approach.
- The connector holds a replication slot, as the outbox connectors do. `acme_products` is a quiet
  database, so its slot lags until a catalog table changes.
