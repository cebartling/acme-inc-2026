package com.acme.order.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * A human-readable order number, `ACME-YYYYMMDD-NNNNNN` (journey 0005 AC-5.4): the day
 * the order was placed and that day's 6-digit sequence number. Like the customer
 * service's `CustomerNumber`.
 */
@JvmInline
value class OrderNumber(val value: String) {

    init {
        require(PATTERN.matches(value)) { "Order number must match ACME-YYYYMMDD-NNNNNN, got: $value" }
    }

    override fun toString(): String = value

    companion object {
        private val PATTERN = Regex("^ACME-\\d{8}-\\d{6}$")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

        fun create(date: LocalDate, sequenceNumber: Int): OrderNumber {
            require(sequenceNumber in 1..999999) {
                "Sequence number must be between 1 and 999999, got: $sequenceNumber"
            }
            return OrderNumber("ACME-${dateKey(date)}-${sequenceNumber.toString().padStart(6, '0')}")
        }

        /** The `YYYYMMDD` key of the day's sequence. */
        fun dateKey(date: LocalDate): String = date.format(DATE_FORMAT)
    }
}
