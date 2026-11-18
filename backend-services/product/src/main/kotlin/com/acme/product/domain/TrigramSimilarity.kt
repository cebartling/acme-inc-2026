package com.acme.product.domain

/**
 * Trigram similarity as PostgreSQL's `pg_trgm` computes it: each word (a run of letters and
 * digits, lower-cased) is padded with two spaces before and one after, the trigrams of all words
 * form a set, and the similarity of two texts is the size of the sets' intersection over the size
 * of their union. MongoDB has no equivalent, so the spelling suggestion and the autocomplete
 * ranking compute it here (PIN-550).
 */
object TrigramSimilarity {

    fun of(a: String, b: String): Double {
        val first = trigrams(a)
        val second = trigrams(b)
        val union = first.union(second).size
        return if (union == 0) 0.0 else first.intersect(second).size.toDouble() / union
    }

    private fun trigrams(text: String): Set<String> =
        WORD.findAll(text.lowercase()).flatMap { word ->
            "  ${word.value} ".windowed(3).asSequence()
        }.toSet()

    private val WORD = Regex("[\\p{L}\\p{N}]+")
}
