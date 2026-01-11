package com.acme.customer.application

import com.acme.customer.api.v1.dto.AddAddressRequest
import com.acme.customer.domain.Address
import com.acme.customer.domain.AddressType
import com.acme.customer.domain.events.AddressAdded
import com.acme.customer.infrastructure.messaging.OutboxWriter
import com.acme.customer.infrastructure.persistence.AddressRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.persistence.EventStoreRepository
import com.acme.customer.infrastructure.security.CustomerIdGenerator
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Use case for adding a new address to a customer profile.
 *
 * This use case orchestrates the address creation flow:
 * 1. Validate the customer exists and user is authorized
 * 2. Check address limit hasn't been reached
 * 3. Check for duplicate labels
 * 4. Validate PO Box restrictions for shipping
 * 5. Handle default address logic
 * 6. Persist the address
 * 7. Publish AddressAdded event via outbox
 */
@Service
class AddAddressUseCase(
    private val customerRepository: CustomerRepository,
    private val addressRepository: AddressRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val outboxWriter: OutboxWriter,
    private val customerIdGenerator: CustomerIdGenerator,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(AddAddressUseCase::class.java)

    private val addressAddedCounter: Counter = Counter.builder("address.added")
        .tag("status", "success")
        .register(meterRegistry)

    private val addressAddedFailureCounter: Counter = Counter.builder("address.added")
        .tag("status", "failure")
        .register(meterRegistry)

    private val addressAddedTimer: Timer = Timer.builder("address.added.duration")
        .register(meterRegistry)

    /**
     * Adds a new address to a customer profile.
     *
     * @param customerId The customer ID to add the address to.
     * @param userId The user ID making the request (for authorization).
     * @param request The address request containing the address details.
     * @param correlationId Correlation ID for distributed tracing.
     * @return The result of the operation.
     */
    @Transactional
    fun execute(
        customerId: UUID,
        userId: UUID,
        request: AddAddressRequest,
        correlationId: UUID
    ): AddAddressResult {
        return addressAddedTimer.record<AddAddressResult> {
            logger.info("Adding address for customer {}", customerId)

            // Find the customer
            val customer = customerRepository.findById(customerId).orElse(null)
                ?: run {
                    logger.warn("Customer not found: {}", customerId)
                    addressAddedFailureCounter.increment()
                    return@record AddAddressResult.CustomerNotFound(customerId)
                }

            // Authorization check: verify the user owns this customer profile
            if (customer.userId != userId) {
                logger.warn(
                    "User {} attempted to add address to customer {} owned by user {}",
                    userId,
                    customerId,
                    customer.userId
                )
                addressAddedFailureCounter.increment()
                return@record AddAddressResult.Unauthorized(customerId, userId)
            }

            // Validate the request
            val validationErrors = validateRequest(request)
            if (validationErrors.isNotEmpty()) {
                logger.debug("Validation failed for address: {}", validationErrors)
                addressAddedFailureCounter.increment()
                return@record AddAddressResult.ValidationFailed(validationErrors)
            }

            val addressType = AddressType.valueOf(request.type.uppercase())

            // Check address limit
            val currentAddressCount = addressRepository.countByCustomerIdAndType(customerId, addressType)
            if (currentAddressCount >= Address.MAX_ADDRESSES_PER_TYPE) {
                logger.warn(
                    "Customer {} has reached maximum addresses ({}) for type {}",
                    customerId,
                    Address.MAX_ADDRESSES_PER_TYPE,
                    addressType
                )
                addressAddedFailureCounter.increment()
                return@record AddAddressResult.MaxAddressesReached(
                    type = addressType.name,
                    maxAllowed = Address.MAX_ADDRESSES_PER_TYPE
                )
            }

            // Check for duplicate label
            request.label?.let { label ->
                if (addressRepository.existsByCustomerIdAndLabel(customerId, label)) {
                    logger.warn("Address label '{}' already exists for customer {}", label, customerId)
                    addressAddedFailureCounter.increment()
                    return@record AddAddressResult.DuplicateLabel(label)
                }
            }

            try {
                // Create the address entity
                val addressId = customerIdGenerator.generate()
                val address = Address.create(
                    id = addressId,
                    customerId = customerId,
                    type = addressType,
                    streetLine1 = request.street.line1,
                    streetLine2 = request.street.line2,
                    city = request.city,
                    state = request.state,
                    postalCode = request.postalCode,
                    country = request.country,
                    label = request.label,
                    isDefault = request.isDefault
                )

                // Check PO Box restriction for shipping
                if (addressType == AddressType.SHIPPING && address.isPOBox()) {
                    logger.warn("PO Box address not allowed for shipping: {}", address.streetLine1)
                    addressAddedFailureCounter.increment()
                    return@record AddAddressResult.POBoxNotAllowedForShipping
                }

                // Handle default address logic
                if (request.isDefault) {
                    // Clear any existing default address for this type
                    val clearedCount = addressRepository.clearDefaultForType(customerId, addressType)
                    if (clearedCount > 0) {
                        logger.debug("Cleared {} previous default address(es) for type {}", clearedCount, addressType)
                    }
                }

                // Save the address
                addressRepository.save(address)

                logger.info(
                    "Added address {} for customer {}, type: {}, default: {}",
                    addressId,
                    customerId,
                    addressType,
                    request.isDefault
                )

                // Create domain event
                val event = AddressAdded.create(
                    customerId = customerId,
                    addressId = addressId,
                    type = addressType.name,
                    label = request.label,
                    isDefault = request.isDefault,
                    isValidated = address.isValidated,
                    correlationId = correlationId,
                    causationId = correlationId
                )

                // Persist event to event store
                eventStoreRepository.append(event)

                // Write to outbox within the transaction
                outboxWriter.write(event, AddressAdded.TOPIC)

                addressAddedCounter.increment()

                AddAddressResult.Success(address)
            } catch (e: Exception) {
                logger.error(
                    "Failed to add address for customer {}: {}",
                    customerId,
                    e.message,
                    e
                )
                addressAddedFailureCounter.increment()
                AddAddressResult.Failure(
                    message = "Failed to add address: ${e.message}",
                    cause = e
                )
            }
        }!!
    }

    /**
     * Validates the add address request.
     *
     * @return Map of field names to error messages. Empty if validation passes.
     */
    private fun validateRequest(request: AddAddressRequest): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        // Validate type
        try {
            AddressType.valueOf(request.type.uppercase())
        } catch (e: IllegalArgumentException) {
            errors["type"] = "Invalid address type. Allowed values: SHIPPING, BILLING"
        }

        // Validate street
        if (request.street.line1.isBlank()) {
            errors["street.line1"] = "Street address is required"
        } else if (request.street.line1.length > 100) {
            errors["street.line1"] = "Street address cannot exceed 100 characters"
        }

        request.street.line2?.let { line2 ->
            if (line2.length > 100) {
                errors["street.line2"] = "Street address line 2 cannot exceed 100 characters"
            }
        }

        // Validate city
        if (request.city.isBlank()) {
            errors["city"] = "City is required"
        } else if (request.city.length > 50) {
            errors["city"] = "City cannot exceed 50 characters"
        }

        // Validate state
        if (request.state.isBlank()) {
            errors["state"] = "State is required"
        } else if (request.state.length > 50) {
            errors["state"] = "State cannot exceed 50 characters"
        }

        // Validate postal code
        if (request.postalCode.isBlank()) {
            errors["postalCode"] = "Postal code is required"
        } else if (request.postalCode.length > 20) {
            errors["postalCode"] = "Postal code cannot exceed 20 characters"
        }

        // Validate country (ISO 3166-1 alpha-2)
        if (request.country.isBlank()) {
            errors["country"] = "Country is required"
        } else if (request.country.length != 2) {
            errors["country"] = "Country must be a 2-letter ISO code (e.g., US, CA, GB)"
        }

        // Validate label if provided
        request.label?.let { label ->
            if (label.length > 50) {
                errors["label"] = "Label cannot exceed 50 characters"
            }
        }

        return errors
    }
}
