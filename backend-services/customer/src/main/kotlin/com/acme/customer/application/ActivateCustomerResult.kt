package com.acme.customer.application

import com.acme.customer.domain.Customer
import java.util.UUID

/**
 * Sealed class representing the result of a customer activation operation.
 *
 * Using a sealed class enables exhaustive when-expressions and
 * type-safe handling of all possible outcomes.
 */
sealed class ActivateCustomerResult {

    /**
     * Customer was successfully activated.
     *
     * @property customer The activated customer.
     */
    data class Success(
        val customer: Customer
    ) : ActivateCustomerResult()

    /**
     * The customer is already active.
     *
     * This typically happens when processing a duplicate event.
     *
     * @property customerId The ID of the already active customer.
     */
    data class AlreadyActive(
        val customerId: UUID
    ) : ActivateCustomerResult()

    /**
     * No customer was found for the given user ID.
     *
     * @property userId The user ID for which no customer exists.
     */
    data class CustomerNotFound(
        val userId: UUID
    ) : ActivateCustomerResult()

    /**
     * The operation failed due to an error.
     *
     * @property message Description of the error.
     * @property cause The underlying exception, if any.
     */
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : ActivateCustomerResult()
}
