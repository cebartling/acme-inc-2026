package com.acme.customer.infrastructure.security

import com.acme.customer.domain.CustomerId
import com.fasterxml.uuid.Generators
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Generator for customer identifiers using UUID v7.
 *
 * UUID v7 is time-ordered, which provides several benefits:
 * - Natural chronological ordering without additional indexes
 * - Better database index locality for recent records
 * - Sortable by creation time
 * - Still globally unique like other UUID versions
 */
@Component
class CustomerIdGenerator {

    private val generator = Generators.timeBasedEpochGenerator()

    /**
     * Generates a new UUID v7 customer identifier.
     *
     * @return A new time-ordered UUID v7.
     */
    fun generate(): UUID = generator.generate()

    /**
     * Generates a new customer ID wrapped in a type-safe [CustomerId].
     *
     * @return A new [CustomerId] containing a UUID v7.
     */
    fun generateCustomerId(): CustomerId = CustomerId(generate())
}
