package com.acme.customer.domain

import java.util.UUID

/**
 * Value class representing a unique customer identifier.
 *
 * This is a type-safe wrapper around [UUID] that provides compile-time
 * type safety for customer identifiers throughout the application.
 * Customer IDs are generated as UUID v7 (time-ordered) for efficient
 * database indexing and natural chronological ordering.
 *
 * @property value The underlying UUID value.
 * @see UUID
 */
@JvmInline
value class CustomerId(val value: UUID) {

    /**
     * Returns the string representation of the underlying UUID.
     *
     * @return The UUID as a string in the standard format.
     */
    override fun toString(): String = value.toString()

    companion object {
        /**
         * Creates a [CustomerId] from a string representation.
         *
         * @param id The string representation of a UUID.
         * @return A new [CustomerId] instance.
         * @throws IllegalArgumentException If the string is not a valid UUID format.
         */
        fun fromString(id: String): CustomerId = CustomerId(UUID.fromString(id))
    }
}
