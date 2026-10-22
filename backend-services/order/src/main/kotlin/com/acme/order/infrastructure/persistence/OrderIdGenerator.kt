package com.acme.order.infrastructure.persistence

import com.fasterxml.uuid.Generators
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * UUID v7 (time-ordered) IDs for orders and their lines (journey 0005 AC-5.4), like the
 * customer service's `CustomerIdGenerator`.
 */
@Component
class OrderIdGenerator {

    private val generator = Generators.timeBasedEpochGenerator()

    fun generate(): UUID = generator.generate()
}
