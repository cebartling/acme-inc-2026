package com.acme.customer.application

import com.acme.customer.domain.Address
import java.util.UUID

/**
 * Result of an update address operation.
 *
 * Uses sealed class pattern (ADR-0020) for exhaustive handling of all outcomes.
 */
sealed class UpdateAddressResult {
    /**
     * Address was successfully updated.
     *
     * @property address The updated address entity.
     * @property changedFields List of field names that were modified.
     */
    data class Success(
        val address: Address,
        val changedFields: List<String>
    ) : UpdateAddressResult()

    /**
     * Address was not found.
     *
     * @property addressId The address ID that was not found.
     */
    data class AddressNotFound(
        val addressId: UUID
    ) : UpdateAddressResult()

    /**
     * Customer was not found.
     *
     * @property customerId The customer ID that was not found.
     */
    data class CustomerNotFound(
        val customerId: UUID
    ) : UpdateAddressResult()

    /**
     * User is not authorized to update this address.
     *
     * @property customerId The customer ID owning the address.
     * @property userId The user ID attempting the operation.
     */
    data class Unauthorized(
        val customerId: UUID,
        val userId: UUID
    ) : UpdateAddressResult()

    /**
     * Validation failed for the update request.
     *
     * @property errors Map of field names to error messages.
     */
    data class ValidationFailed(
        val errors: Map<String, String>
    ) : UpdateAddressResult()

    /**
     * Address label already exists for this customer.
     *
     * @property label The duplicate label.
     */
    data class DuplicateLabel(
        val label: String
    ) : UpdateAddressResult()

    /**
     * PO Box address cannot be used for shipping.
     */
    data object POBoxNotAllowedForShipping : UpdateAddressResult()

    /**
     * No updates were provided in the request.
     */
    data object NoUpdates : UpdateAddressResult()

    /**
     * An unexpected error occurred.
     *
     * @property message Error message.
     * @property cause The underlying exception.
     */
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : UpdateAddressResult()
}
