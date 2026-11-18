package com.acme.product.domain

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The expected values are what PostgreSQL's `pg_trgm` `similarity(a, b)` returned for the same
 * pairs, which the spelling suggestion and autocomplete ranking used before PIN-550.
 */
class TrigramSimilarityTest {

    @ParameterizedTest
    @CsvSource(
        "widgit,Premium Widget,0.22222222",
        "widgit,Widget Starter Kit,0.23809524",
        "widgit,widget,0.4",
        "Widget,Widget,1.0",
        "smart watch,Smart Watch Pro,0.75",
        "xyz,abc,0.0",
        "a,a,1.0",
        "Wi,Widget,0.25",
        "ACME,acme-widget,0.41666666",
        "O'Brien,o'brien,1.0"
    )
    fun `similarity matches pg_trgm`(a: String, b: String, expected: Double) {
        assertEquals(expected, TrigramSimilarity.of(a, b), 1e-6)
    }

    @Test
    fun `similarity is symmetric`() {
        assertEquals(TrigramSimilarity.of("widgit", "Premium Widget"), TrigramSimilarity.of("Premium Widget", "widgit"))
    }

    @Test
    fun `text with no letters or digits has no trigrams and is not similar to anything`() {
        assertEquals(0.0, TrigramSimilarity.of("  ", ""))
        assertEquals(0.0, TrigramSimilarity.of("--", "widget"))
    }
}
