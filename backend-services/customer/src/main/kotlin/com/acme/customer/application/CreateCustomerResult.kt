package com.acme.customer.application

import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import java.util.UUID

/**
 * Sealed class representing the result of a customer creation operation.
 *
 * Using a sealed class enables exhaustive when-expressions and
 * type-safe handling of all possible outcomes.
 */
sealed class CreateCustomerResult {

    /**
     * Customer was successfully created.
     *
     * @property customer The newly created customer.
     * @property preferences The customer's preferences.
     */
    data class Success(
        val customer: Customer,
        val preferences: CustomerPreferences
    ) : CreateCustomerResult()

    /**
     * A customer already exists for this user ID.
     *
     * This typically happens when processing a duplicate event.
     *
     * @property userId The user ID that already has a customer profile.
     * @property existingCustomerId The ID of the existing customer.
     */
    data class AlreadyExists(
        val userId: UUID,
        val existingCustomerId: UUID
    ) : CreateCustomerResult()

    /**
     * The operation failed due to an error.
     *
     * @property message Description of the error.
     * @property cause The underlying exception, if any.
     */
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : CreateCustomerResult()
}
