package com.acme.order.infrastructure.persistence

import com.acme.order.domain.OrderNumber
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * Hands out each day's order number sequence through the `next_order_number` function
 * (V3), an atomic upsert, like the customer service's `CustomerNumberSequenceRepository`.
 */
@Repository
class OrderNumberSequenceRepository(
    private val jdbcTemplate: JdbcTemplate
) {
    fun nextOrderNumber(date: LocalDate): OrderNumber {
        val dateKey = OrderNumber.dateKey(date)
        val sequence = jdbcTemplate.queryForObject("SELECT next_order_number(?)", Int::class.java, dateKey)
            ?: throw IllegalStateException("next_order_number returned no value for $dateKey")
        return OrderNumber.create(date, sequence)
    }
}
