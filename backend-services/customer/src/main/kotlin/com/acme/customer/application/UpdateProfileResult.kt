package com.acme.customer.application

import com.acme.customer.domain.Customer
import java.util.UUID

/**
 * Result of an update profile operation.
 *
 * Uses sealed class pattern (ADR-0020) for exhaustive handling of all outcomes.
 */
sealed class UpdateProfileResult {
    /**
     * Profile was successfully updated.
     *
     * @property customer The updated customer entity.
     * @property changedFields List of field names that were modified.
     */
    data class Success(
        val customer: Customer,
        val changedFields: List<String>
    ) : UpdateProfileResult()

    /**
     * Customer was not found.
     *
     * @property customerId The customer ID that was not found.
     */
    data class NotFound(
        val customerId: UUID
    ) : UpdateProfileResult()

    /**
     * Customer is not authorized to update this profile.
     *
     * @property customerId The customer ID being updated.
     * @property userId The user ID attempting the update.
     */
    data class Unauthorized(
        val customerId: UUID,
        val userId: UUID
    ) : UpdateProfileResult()

    /**
     * Validation failed for the update request.
     *
     * @property errors Map of field names to error messages.
     */
    data class ValidationFailed(
        val errors: Map<String, String>
    ) : UpdateProfileResult()

    /**
     * No updates were provided in the request.
     */
    data object NoUpdates : UpdateProfileResult()

    /**
     * An unexpected error occurred.
     *
     * @property message Error message.
     * @property cause The underlying exception.
     */
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : UpdateProfileResult()
}
