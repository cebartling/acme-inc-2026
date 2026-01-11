package com.acme.customer.application

import java.util.UUID

/**
 * Result of a remove address operation.
 *
 * Uses sealed class pattern (ADR-0020) for exhaustive handling of all outcomes.
 */
sealed class RemoveAddressResult {
    /**
     * Address was successfully removed.
     *
     * @property addressId The ID of the removed address.
     * @property wasDefault Whether the removed address was a default address.
     */
    data class Success(
        val addressId: UUID,
        val wasDefault: Boolean
    ) : RemoveAddressResult()

    /**
     * Address was not found.
     *
     * @property addressId The address ID that was not found.
     */
    data class AddressNotFound(
        val addressId: UUID
    ) : RemoveAddressResult()

    /**
     * Customer was not found.
     *
     * @property customerId The customer ID that was not found.
     */
    data class CustomerNotFound(
        val customerId: UUID
    ) : RemoveAddressResult()

    /**
     * User is not authorized to remove this address.
     *
     * @property customerId The customer ID owning the address.
     * @property userId The user ID attempting the operation.
     */
    data class Unauthorized(
        val customerId: UUID,
        val userId: UUID
    ) : RemoveAddressResult()

    /**
     * An unexpected error occurred.
     *
     * @property message Error message.
     * @property cause The underlying exception.
     */
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : RemoveAddressResult()
}
