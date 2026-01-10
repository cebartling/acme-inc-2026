package com.acme.customer.application

import com.acme.customer.domain.events.AddressRemoved
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
 * Use case for removing an address from a customer profile.
 *
 * This use case orchestrates the address removal flow:
 * 1. Validate the address and customer exist
 * 2. Verify user authorization
 * 3. Delete the address
 * 4. Publish AddressRemoved event via outbox
 */
@Service
class RemoveAddressUseCase(
    private val customerRepository: CustomerRepository,
    private val addressRepository: AddressRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val outboxWriter: OutboxWriter,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(RemoveAddressUseCase::class.java)

    private val addressRemovedCounter: Counter = Counter.builder("address.removed")
        .tag("status", "success")
        .register(meterRegistry)

    private val addressRemovedFailureCounter: Counter = Counter.builder("address.removed")
        .tag("status", "failure")
        .register(meterRegistry)

    private val addressRemovedTimer: Timer = Timer.builder("address.removed.duration")
        .register(meterRegistry)

    /**
     * Removes an address from a customer profile.
     *
     * @param customerId The customer ID that owns the address.
     * @param addressId The address ID to remove.
     * @param userId The user ID making the request (for authorization).
     * @param correlationId Correlation ID for distributed tracing.
     * @return The result of the operation.
     */
    @Transactional
    fun execute(
        customerId: UUID,
        addressId: UUID,
        userId: UUID,
        correlationId: UUID
    ): RemoveAddressResult {
        return addressRemovedTimer.record<RemoveAddressResult> {
            logger.info("Removing address {} for customer {}", addressId, customerId)

            // Find the customer
            val customer = customerRepository.findById(customerId).orElse(null)
                ?: run {
                    logger.warn("Customer not found: {}", customerId)
                    addressRemovedFailureCounter.increment()
                    return@record RemoveAddressResult.CustomerNotFound(customerId)
                }

            // Authorization check
            if (customer.userId != userId) {
                logger.warn(
                    "User {} attempted to remove address from customer {} owned by user {}",
                    userId,
                    customerId,
                    customer.userId
                )
                addressRemovedFailureCounter.increment()
                return@record RemoveAddressResult.Unauthorized(customerId, userId)
            }

            // Find the address
            val address = addressRepository.findById(addressId).orElse(null)
                ?: run {
                    logger.warn("Address not found: {}", addressId)
                    addressRemovedFailureCounter.increment()
                    return@record RemoveAddressResult.AddressNotFound(addressId)
                }

            // Verify address belongs to customer
            if (address.customerId != customerId) {
                logger.warn("Address {} does not belong to customer {}", addressId, customerId)
                addressRemovedFailureCounter.increment()
                return@record RemoveAddressResult.AddressNotFound(addressId)
            }

            try {
                val wasDefault = address.isDefault
                val addressType = address.type.name

                // Delete the address
                addressRepository.delete(address)

                logger.info(
                    "Removed address {} for customer {}, was default: {}",
                    addressId,
                    customerId,
                    wasDefault
                )

                // Create domain event
                val event = AddressRemoved.create(
                    customerId = customerId,
                    addressId = addressId,
                    type = addressType,
                    wasDefault = wasDefault,
                    correlationId = correlationId,
                    causationId = correlationId
                )

                // Persist event to event store
                eventStoreRepository.append(event)

                // Write to outbox within the transaction
                outboxWriter.write(event, AddressRemoved.TOPIC)

                addressRemovedCounter.increment()

                RemoveAddressResult.Success(
                    addressId = addressId,
                    wasDefault = wasDefault
                )
            } catch (e: Exception) {
                logger.error(
                    "Failed to remove address {} for customer {}: {}",
                    addressId,
                    customerId,
                    e.message,
                    e
                )
                addressRemovedFailureCounter.increment()
                RemoveAddressResult.Failure(
                    message = "Failed to remove address: ${e.message}",
                    cause = e
                )
            }
        }!!
    }
}
