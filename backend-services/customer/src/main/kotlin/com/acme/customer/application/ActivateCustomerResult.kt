package com.acme.customer.application

import arrow.core.Either
import com.acme.customer.domain.Customer
import java.util.UUID

/**
 * Sealed interface representing possible customer activation errors.
 *
 * Using Arrow's Either with a sealed error hierarchy provides type-safe
 * error handling with exhaustive pattern matching.
 */
sealed interface ActivateCustomerError {
    /**
     * The customer is already active.
     *
     * This typically happens when processing a duplicate event.
     *
     * @property customerId The ID of the already active customer.
     */
    data class AlreadyActive(
        val customerId: UUID
    ) : ActivateCustomerError

    /**
     * No customer was found for the given user ID.
     *
     * @property userId The user ID for which no customer exists.
     */
    data class CustomerNotFound(
        val userId: UUID
    ) : ActivateCustomerError

    /**
     * The operation failed due to an error.
     *
     * @property message Description of the error.
     * @property cause The underlying exception, if any.
     */
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : ActivateCustomerError
}

/**
 * Response data for successful customer activation.
 *
 * @property customer The activated customer.
 */
data class ActivateCustomerSuccess(
    val customer: Customer
)

/**
 * Type alias for the customer activation result using Arrow's Either.
 */
typealias ActivateCustomerResult = Either<ActivateCustomerError, ActivateCustomerSuccess>
