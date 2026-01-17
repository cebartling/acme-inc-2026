package com.acme.customer.application

import arrow.core.Either
import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import java.util.UUID

/**
 * Sealed interface representing possible customer creation errors.
 *
 * Using Arrow's Either with a sealed error hierarchy provides type-safe
 * error handling with exhaustive pattern matching.
 */
sealed interface CreateCustomerError {
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
    ) : CreateCustomerError

    /**
     * The operation failed due to an error.
     *
     * @property message Description of the error.
     * @property cause The underlying exception, if any.
     */
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : CreateCustomerError
}

/**
 * Response data for successful customer creation.
 *
 * @property customer The newly created customer.
 * @property preferences The customer's preferences.
 */
data class CreateCustomerSuccess(
    val customer: Customer,
    val preferences: CustomerPreferences
)

/**
 * Type alias for the customer creation result using Arrow's Either.
 */
typealias CreateCustomerResult = Either<CreateCustomerError, CreateCustomerSuccess>
