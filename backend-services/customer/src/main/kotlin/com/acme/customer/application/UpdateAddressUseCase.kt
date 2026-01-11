package com.acme.customer.application

import com.acme.customer.api.v1.dto.UpdateAddressRequest
import com.acme.customer.domain.AddressType
import com.acme.customer.domain.events.AddressUpdated
import com.acme.customer.infrastructure.messaging.OutboxWriter
import com.acme.customer.infrastructure.persistence.AddressRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.persistence.EventStoreRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Use case for updating an existing address.
 *
 * This use case orchestrates the address update flow:
 * 1. Validate the address and customer exist
 * 2. Verify user authorization
 * 3. Check for duplicate labels
 * 4. Validate PO Box restrictions for shipping
 * 5. Handle default address logic
 * 6. Persist the changes
 * 7. Publish AddressUpdated event via outbox
 */
@Service
class UpdateAddressUseCase(
    private val customerRepository: CustomerRepository,
    private val addressRepository: AddressRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val outboxWriter: OutboxWriter,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(UpdateAddressUseCase::class.java)

    private val addressUpdatedCounter: Counter = Counter.builder("address.updated")
        .tag("status", "success")
        .register(meterRegistry)

    private val addressUpdatedFailureCounter: Counter = Counter.builder("address.updated")
        .tag("status", "failure")
        .register(meterRegistry)

    private val addressUpdatedTimer: Timer = Timer.builder("address.updated.duration")
        .register(meterRegistry)

    /**
     * Updates an existing address.
     *
     * @param customerId The customer ID that owns the address.
     * @param addressId The address ID to update.
     * @param userId The user ID making the request (for authorization).
     * @param request The update request containing fields to update.
     * @param correlationId Correlation ID for distributed tracing.
     * @return The result of the operation.
     */
    @Transactional
    fun execute(
        customerId: UUID,
        addressId: UUID,
        userId: UUID,
        request: UpdateAddressRequest,
        correlationId: UUID
    ): UpdateAddressResult {
        return addressUpdatedTimer.record<UpdateAddressResult> {
            logger.info("Updating address {} for customer {}", addressId, customerId)

            // Check if there are any updates
            if (!request.hasUpdates()) {
                logger.debug("No updates provided for address {}", addressId)
                return@record UpdateAddressResult.NoUpdates
            }

            // Find the customer
            val customer = customerRepository.findById(customerId).orElse(null)
                ?: run {
                    logger.warn("Customer not found: {}", customerId)
                    addressUpdatedFailureCounter.increment()
                    return@record UpdateAddressResult.CustomerNotFound(customerId)
                }

            // Authorization check
            if (customer.userId != userId) {
                logger.warn(
                    "User {} attempted to update address for customer {} owned by user {}",
                    userId,
                    customerId,
                    customer.userId
                )
                addressUpdatedFailureCounter.increment()
                return@record UpdateAddressResult.Unauthorized(customerId, userId)
            }

            // Find the address
            val address = addressRepository.findById(addressId).orElse(null)
                ?: run {
                    logger.warn("Address not found: {}", addressId)
                    addressUpdatedFailureCounter.increment()
                    return@record UpdateAddressResult.AddressNotFound(addressId)
                }

            // Verify address belongs to customer
            if (address.customerId != customerId) {
                logger.warn("Address {} does not belong to customer {}", addressId, customerId)
                addressUpdatedFailureCounter.increment()
                return@record UpdateAddressResult.AddressNotFound(addressId)
            }

            // Validate the request
            val validationErrors = validateRequest(request)
            if (validationErrors.isNotEmpty()) {
                logger.debug("Validation failed for address update: {}", validationErrors)
                addressUpdatedFailureCounter.increment()
                return@record UpdateAddressResult.ValidationFailed(validationErrors)
            }

            // Check for duplicate label (excluding current address)
            request.label?.let { label ->
                if (addressRepository.existsByCustomerIdAndLabelExcluding(customerId, label, addressId)) {
                    logger.warn("Address label '{}' already exists for customer {}", label, customerId)
                    addressUpdatedFailureCounter.increment()
                    return@record UpdateAddressResult.DuplicateLabel(label)
                }
            }

            try {
                val changedFields = mutableListOf<String>()

                // Apply updates
                request.type?.let { typeStr ->
                    val newType = AddressType.valueOf(typeStr.uppercase())
                    if (address.type != newType) {
                        address.updateType(newType)
                        changedFields.add("type")
                    }
                }

                request.street?.let { street ->
                    address.updateDetails(
                        streetLine1 = street.line1,
                        streetLine2 = street.line2,
                        city = request.city ?: address.city,
                        state = request.state ?: address.state,
                        postalCode = request.postalCode ?: address.postalCode,
                        country = request.country ?: address.country,
                        label = request.label ?: address.label
                    )
                    changedFields.add("street")
                    if (request.city != null) changedFields.add("city")
                    if (request.state != null) changedFields.add("state")
                    if (request.postalCode != null) changedFields.add("postalCode")
                    if (request.country != null) changedFields.add("country")
                }

                // Handle individual field updates without street
                if (request.street == null) {
                    var needsDetailUpdate = false
                    val newCity = request.city ?: address.city
                    val newState = request.state ?: address.state
                    val newPostalCode = request.postalCode ?: address.postalCode
                    val newCountry = request.country ?: address.country
                    val newLabel = request.label ?: address.label

                    if (request.city != null && request.city != address.city) {
                        changedFields.add("city")
                        needsDetailUpdate = true
                    }
                    if (request.state != null && request.state != address.state) {
                        changedFields.add("state")
                        needsDetailUpdate = true
                    }
                    if (request.postalCode != null && request.postalCode != address.postalCode) {
                        changedFields.add("postalCode")
                        needsDetailUpdate = true
                    }
                    if (request.country != null && request.country != address.country) {
                        changedFields.add("country")
                        needsDetailUpdate = true
                    }
                    if (request.label != null && request.label != address.label) {
                        changedFields.add("label")
                        needsDetailUpdate = true
                    }

                    if (needsDetailUpdate) {
                        address.updateDetails(
                            streetLine1 = address.streetLine1,
                            streetLine2 = address.streetLine2,
                            city = newCity,
                            state = newState,
                            postalCode = newPostalCode,
                            country = newCountry,
                            label = newLabel
                        )
                    }
                }

                // Check PO Box restriction for shipping
                val effectiveType = request.type?.let { AddressType.valueOf(it.uppercase()) } ?: address.type
                if (effectiveType == AddressType.SHIPPING && address.isPOBox()) {
                    logger.warn("PO Box address not allowed for shipping: {}", address.streetLine1)
                    addressUpdatedFailureCounter.increment()
                    return@record UpdateAddressResult.POBoxNotAllowedForShipping
                }

                // Handle default address logic
                request.isDefault?.let { isDefault ->
                    if (isDefault && !address.isDefault) {
                        // Clear any existing default address for this type
                        addressRepository.clearDefaultForType(customerId, effectiveType)
                        address.setAsDefault(true)
                        changedFields.add("isDefault")
                    } else if (!isDefault && address.isDefault) {
                        address.setAsDefault(false)
                        changedFields.add("isDefault")
                    }
                }

                // Save the address
                addressRepository.save(address)

                logger.info(
                    "Updated address {} for customer {}, changed fields: {}",
                    addressId,
                    customerId,
                    changedFields
                )

                // Create domain event
                val event = AddressUpdated.create(
                    customerId = customerId,
                    addressId = addressId,
                    changedFields = changedFields,
                    isDefault = address.isDefault,
                    isValidated = address.isValidated,
                    correlationId = correlationId,
                    causationId = correlationId
                )

                // Persist event to event store
                eventStoreRepository.append(event)

                // Write to outbox within the transaction
                outboxWriter.write(event, AddressUpdated.TOPIC)

                addressUpdatedCounter.increment()

                UpdateAddressResult.Success(
                    address = address,
                    changedFields = changedFields
                )
            } catch (e: Exception) {
                logger.error(
                    "Failed to update address {} for customer {}: {}",
                    addressId,
                    customerId,
                    e.message,
                    e
                )
                addressUpdatedFailureCounter.increment()
                UpdateAddressResult.Failure(
                    message = "Failed to update address: ${e.message}",
                    cause = e
                )
            }
        }!!
    }

    /**
     * Validates the update request.
     *
     * @return Map of field names to error messages. Empty if validation passes.
     */
    private fun validateRequest(request: UpdateAddressRequest): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        // Validate type if provided
        request.type?.let { typeStr ->
            try {
                AddressType.valueOf(typeStr.uppercase())
            } catch (e: IllegalArgumentException) {
                errors["type"] = "Invalid address type. Allowed values: SHIPPING, BILLING"
            }
        }

        // Validate street if provided
        request.street?.let { street ->
            if (street.line1.isBlank()) {
                errors["street.line1"] = "Street address is required"
            } else if (street.line1.length > 100) {
                errors["street.line1"] = "Street address cannot exceed 100 characters"
            }

            street.line2?.let { line2 ->
                if (line2.length > 100) {
                    errors["street.line2"] = "Street address line 2 cannot exceed 100 characters"
                }
            }
        }

        // Validate city if provided
        request.city?.let { city ->
            if (city.isBlank()) {
                errors["city"] = "City cannot be blank"
            } else if (city.length > 50) {
                errors["city"] = "City cannot exceed 50 characters"
            }
        }

        // Validate state if provided
        request.state?.let { state ->
            if (state.isBlank()) {
                errors["state"] = "State cannot be blank"
            } else if (state.length > 50) {
                errors["state"] = "State cannot exceed 50 characters"
            }
        }

        // Validate postal code if provided
        request.postalCode?.let { postalCode ->
            if (postalCode.isBlank()) {
                errors["postalCode"] = "Postal code cannot be blank"
            } else if (postalCode.length > 20) {
                errors["postalCode"] = "Postal code cannot exceed 20 characters"
            }
        }

        // Validate country if provided
        request.country?.let { country ->
            if (country.isBlank()) {
                errors["country"] = "Country cannot be blank"
            } else if (country.length != 2) {
                errors["country"] = "Country must be a 2-letter ISO code (e.g., US, CA, GB)"
            }
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
