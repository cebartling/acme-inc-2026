package com.acme.identity.domain

import java.util.UUID

/**
 * Value class representing a unique user identifier.
 *
 * This is a type-safe wrapper around [UUID] that provides compile-time
 * type safety for user identifiers throughout the application.
 *
 * @property value The underlying UUID value.
 * @see UUID
 */
@JvmInline
value class UserId(val value: UUID) {

    /**
     * Returns the string representation of the underlying UUID.
     *
     * @return The UUID as a string in the standard format.
     */
    override fun toString(): String = value.toString()

    companion object {
        /**
         * Creates a [UserId] from a string representation.
         *
         * @param id The string representation of a UUID.
         * @return A new [UserId] instance.
         * @throws IllegalArgumentException If the string is not a valid UUID format.
         */
        fun fromString(id: String): UserId = UserId(UUID.fromString(id))
    }
}
