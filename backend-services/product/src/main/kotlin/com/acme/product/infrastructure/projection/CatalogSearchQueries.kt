package com.acme.product.infrastructure.projection

import com.acme.product.domain.ProductSuggestion
import com.acme.product.domain.ProductSummary
import com.acme.product.domain.SearchFilters
import com.acme.product.domain.SortOption
import com.acme.product.domain.TrigramSimilarity
import jakarta.annotation.PostConstruct
import org.bson.Document
import org.bson.types.Decimal128
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.index.TextIndexDefinition
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.TextCriteria
import org.springframework.data.mongodb.core.query.TextQuery
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

/**
 * Product search and autocomplete over the catalog read model (PIN-550), replacing PostgreSQL's
 * `tsvector` full-text search and `pg_trgm`. Published products only.
 *
 * - **Search** uses a MongoDB text index weighted like `search_vector` (name over description over
 *   category), with English stemming. A single word is matched as it is, so "widgets" finds
 *   "widget". Several words must all match, as `plainto_tsquery` required; MongoDB would match any
 *   of them, so each word is quoted, which matches the exact word with no stemming. Punctuation in
 *   the query is dropped, so it can never act as search syntax.
 * - **Spelling suggestion and autocomplete ranking** need `pg_trgm`'s similarity, which MongoDB
 *   lacks; [TrigramSimilarity] computes it over the product names, which is fine for a catalog this
 *   size and would need a different approach for a large one.
 */
@Component
class CatalogSearchQueries(
    private val mongoTemplate: MongoTemplate
) {

    @PostConstruct
    fun ensureIndexes() {
        mongoTemplate.indexOps(CatalogReadModel.VIEW).ensureIndex(
            TextIndexDefinition.builder()
                .onField("name", 10f)
                .onField("description", 4f)
                .onField("category", 2f)
                .withDefaultLanguage("english")
                .build()
        )
    }

    fun search(text: String, filters: SearchFilters, sort: SortOption, offset: Int, limit: Int): List<ProductSummary> {
        val textCriteria = textCriteria(text) ?: return emptyList()
        val query = TextQuery(textCriteria).also { it.addCriteria(filterCriteria(filters)) }
        when (sort) {
            SortOption.RELEVANCE -> query.sortByScore().with(Sort.by(Sort.Order.asc("_id")))
            SortOption.PRICE_ASC -> query.with(Sort.by(Sort.Order.asc("price"), Sort.Order.asc("_id")))
            SortOption.PRICE_DESC -> query.with(Sort.by(Sort.Order.desc("price"), Sort.Order.asc("_id")))
            SortOption.NEWEST -> query.with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("_id")))
        }
        return mongoTemplate.find(query.skip(offset.toLong()).limit(limit), Document::class.java, CatalogReadModel.VIEW)
            .map { it.toProductSummary() }
    }

    fun count(text: String, filters: SearchFilters): Long {
        val textCriteria = textCriteria(text) ?: return 0
        return mongoTemplate.count(TextQuery(textCriteria).also { it.addCriteria(filterCriteria(filters)) }, CatalogReadModel.VIEW)
    }

    /**
     * The matches per category, most first. The category filter is left out on purpose, so every
     * category option stays visible while one is selected; the price range applies.
     */
    fun categoryFacets(text: String, priceMin: BigDecimal?, priceMax: BigDecimal?): Map<String, Long> {
        val textCriteria = textCriteria(text) ?: return emptyMap()
        val filters = filterCriteria(SearchFilters(priceMin = priceMin, priceMax = priceMax)).and("category").ne(null)
        val rows = mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(textCriteria),
                Aggregation.match(filters),
                Aggregation.group("category").count().`as`("count"),
                Aggregation.sort(Sort.by(Sort.Order.desc("count"), Sort.Order.asc("_id")))
            ),
            CatalogReadModel.VIEW,
            Document::class.java
        ).mappedResults
        return rows.associate { it.getString("_id") to it.getInteger("count").toLong() }
    }

    /** The product name most similar to [text], if any is similar enough (`pg_trgm`'s threshold of 0.1). */
    fun spellingSuggestion(text: String): String? =
        publishedNames()
            .map { it to TrigramSimilarity.of(it, text) }
            .filter { (_, similarity) -> similarity > SIMILARITY_THRESHOLD }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
            .firstOrNull()?.first

    /** Products whose name starts with [prefix], most similar to it first, then by name. */
    fun autocompleteProducts(prefix: String, limit: Int): List<ProductSuggestion> =
        mongoTemplate.find(
            Query(published().and("name").regex(startsWith(prefix), "i")),
            Document::class.java,
            CatalogReadModel.VIEW
        )
            .map { ProductSuggestion(UUID.fromString(it.getString("_id")), it.getString("name"), it.getString("slug")) }
            .sortedWith(compareByDescending<ProductSuggestion> { TrigramSimilarity.of(it.name, prefix) }.thenBy { it.name })
            .take(limit)

    /** The distinct categories that start with [prefix], alphabetically without regard to case. */
    fun autocompleteCategories(prefix: String, limit: Int): List<String> =
        mongoTemplate.findDistinct(
            Query(published().and("category").regex(startsWith(prefix), "i")),
            "category",
            CatalogReadModel.VIEW,
            String::class.java
        ).sortedWith(String.CASE_INSENSITIVE_ORDER).take(limit)

    private fun publishedNames(): List<String> =
        mongoTemplate.find(
            Query(published()).apply { fields().include("name") },
            Document::class.java,
            CatalogReadModel.VIEW
        ).map { it.getString("name") }

    private fun published() = Criteria.where("status").`is`("PUBLISHED")

    private fun filterCriteria(filters: SearchFilters): Criteria {
        val criteria = published()
        if (filters.categories.isNotEmpty()) criteria.and("category").`in`(filters.categories)
        // Prices are stored as Decimal128, which a BigDecimal would not be converted to
        if (filters.priceMin != null || filters.priceMax != null) {
            val price = criteria.and("price")
            filters.priceMin?.let { price.gte(Decimal128(it)) }
            filters.priceMax?.let { price.lte(Decimal128(it)) }
        }
        return criteria
    }

    /** Null when [text] has no word to search for. */
    private fun textCriteria(text: String): TextCriteria? {
        val words = WORD.findAll(text).map { it.value }.toList()
        return when (words.size) {
            0 -> null
            1 -> TextCriteria.forDefaultLanguage().matching(words.single())
            else -> words.fold(TextCriteria.forDefaultLanguage()) { criteria, word -> criteria.matchingPhrase(word) }
        }
    }

    private fun startsWith(prefix: String) = "^" + prefix.replace(REGEX_SPECIAL) { "\\" + it.value }

    private companion object {
        const val SIMILARITY_THRESHOLD = 0.1
        val WORD = Regex("[\\p{L}\\p{N}]+")
        val REGEX_SPECIAL = Regex("""[\\^$.|?*+()\[\]{}]""")
    }
}
