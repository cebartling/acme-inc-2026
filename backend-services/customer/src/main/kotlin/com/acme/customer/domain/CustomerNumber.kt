package com.acme.customer.domain

import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Value class representing a human-readable customer number.
 *
 * Customer numbers follow the format: ACME-YYYYMM-NNNNNN
 * - ACME: Platform prefix
 * - YYYYMM: Year and month of registration
 * - NNNNNN: Sequential 6-digit number (zero-padded)
 *
 * Example: ACME-202601-000142
 *
 * @property value The formatted customer number string.
 */
@JvmInline
value class CustomerNumber(val value: String) {

    init {
        require(PATTERN.matches(value)) {
            "Customer number must match format ACME-YYYYMM-NNNNNN, got: $value"
        }
    }

    /**
     * Returns the customer number string.
     *
     * @return The formatted customer number.
     */
    override fun toString(): String = value

    companion object {
        private const val PREFIX = "ACME"
        private val PATTERN = Regex("^ACME-\\d{6}-\\d{6}$")
        private val YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM")

        /**
         * Creates a new customer number from year-month and sequence number.
         *
         * @param yearMonth The year and month of registration.
         * @param sequenceNumber The sequential number within the month (1-999999).
         * @return A new [CustomerNumber] instance.
         * @throws IllegalArgumentException If sequence number is out of range.
         */
        fun create(yearMonth: YearMonth, sequenceNumber: Int): CustomerNumber {
            require(sequenceNumber in 1..999999) {
                "Sequence number must be between 1 and 999999, got: $sequenceNumber"
            }
            val yearMonthStr = yearMonth.format(YEAR_MONTH_FORMAT)
            val sequenceStr = sequenceNumber.toString().padStart(6, '0')
            return CustomerNumber("$PREFIX-$yearMonthStr-$sequenceStr")
        }

        /**
         * Parses a customer number string.
         *
         * @param value The customer number string to parse.
         * @return A new [CustomerNumber] instance.
         * @throws IllegalArgumentException If the format is invalid.
         */
        fun parse(value: String): CustomerNumber = CustomerNumber(value)

        /**
         * Extracts the year-month key (YYYYMM format) used for sequence lookup.
         *
         * @param yearMonth The year and month.
         * @return The year-month key string (e.g., "202601").
         */
        fun yearMonthKey(yearMonth: YearMonth): String = yearMonth.format(YEAR_MONTH_FORMAT)
    }
}
