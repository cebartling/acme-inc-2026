package com.acme.customer.infrastructure.persistence

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.YearMonth

/**
 * Repository for atomic customer number sequence generation.
 *
 * Uses PostgreSQL function for thread-safe atomic increment
 * of customer number sequences per year-month.
 */
@Repository
class CustomerNumberSequenceRepository(
    private val jdbcTemplate: JdbcTemplate
) {
    private val logger = LoggerFactory.getLogger(CustomerNumberSequenceRepository::class.java)

    /**
     * Gets the next customer number sequence for the given year-month.
     *
     * This operation is atomic and thread-safe, using PostgreSQL's
     * INSERT ... ON CONFLICT for atomic upsert.
     *
     * @param yearMonth The year and month for sequence generation.
     * @return The next sequence number (1-999999).
     */
    fun nextSequence(yearMonth: YearMonth): Int {
        val yearMonthKey = yearMonth.toString().replace("-", "")

        logger.debug("Getting next customer number sequence for {}", yearMonthKey)

        val sequence = jdbcTemplate.queryForObject(
            "SELECT next_customer_number(?)",
            Int::class.java,
            yearMonthKey
        ) ?: throw IllegalStateException("Failed to generate customer number sequence")

        logger.debug("Generated sequence {} for {}", sequence, yearMonthKey)

        return sequence
    }

    /**
     * Gets the current sequence value for a year-month without incrementing.
     *
     * @param yearMonth The year and month to query.
     * @return The current sequence value, or 0 if no sequences exist for this month.
     */
    fun getCurrentSequence(yearMonth: YearMonth): Int {
        val yearMonthKey = yearMonth.toString().replace("-", "")

        return jdbcTemplate.queryForObject(
            "SELECT COALESCE(current_value, 0) FROM customer_number_sequences WHERE year_month = ?",
            Int::class.java,
            yearMonthKey
        ) ?: 0
    }
}
