package com.acme.customer.application

import com.acme.customer.domain.Address
import java.util.UUID

/**
 * Result of an add address operation.
 *
 * Uses sealed class pattern (ADR-0020) for exhaustive handling of all outcomes.
 */
sealed class AddAddressResult {
    /**
     * Address was successfully added.
     *
     * @property address The newly created address entity.
     */
    data class Success(
        val address: Address
    ) : AddAddressResult()

    /**
     * Customer was not found.
     *
     * @property customerId The customer ID that was not found.
     */
    data class CustomerNotFound(
        val customerId: UUID
    ) : AddAddressResult()

    /**
     * User is not authorized to add address to this customer profile.
     *
     * @property customerId The customer ID being modified.
     * @property userId The user ID attempting the operation.
     */
    data class Unauthorized(
        val customerId: UUID,
        val userId: UUID
    ) : AddAddressResult()

    /**
     * Validation failed for the address request.
     *
     * @property errors Map of field names to error messages.
     */
    data class ValidationFailed(
        val errors: Map<String, String>
    ) : AddAddressResult()

    /**
     * Address validation returned suggestions instead of confirming the address.
     *
     * @property message Explanation of the validation issue.
     * @property suggestions List of suggested corrected addresses.
     * @property validationDetails Details about what was corrected.
     */
    data class AddressValidationNeeded(
        val message: String,
        val suggestions: List<AddressSuggestion>,
        val validationDetails: Map<String, String>
    ) : AddAddressResult()

    /**
     * Address label already exists for this customer.
     *
     * @property label The duplicate label.
     */
    data class DuplicateLabel(
        val label: String
    ) : AddAddressResult()

    /**
     * Maximum number of addresses reached for this type.
     *
     * @property type The address type (SHIPPING or BILLING).
     * @property maxAllowed The maximum number of addresses allowed.
     */
    data class MaxAddressesReached(
        val type: String,
        val maxAllowed: Int
    ) : AddAddressResult()

    /**
     * PO Box address cannot be used for shipping.
     */
    data object POBoxNotAllowedForShipping : AddAddressResult()

    /**
     * An unexpected error occurred.
     *
     * @property message Error message.
     * @property cause The underlying exception.
     */
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : AddAddressResult()
}

/**
 * Represents a suggested address correction from validation.
 */
data class AddressSuggestion(
    val streetLine1: String,
    val streetLine2: String?,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String
)
