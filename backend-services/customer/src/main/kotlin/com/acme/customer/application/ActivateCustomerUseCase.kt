package com.acme.customer.application

import com.acme.customer.domain.events.CustomerActivated
import com.acme.customer.infrastructure.messaging.OutboxWriter
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.persistence.EventStoreRepository
import com.acme.customer.infrastructure.projection.CustomerReadModelProjector
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Use case for activating a customer profile.
 *
 * This use case orchestrates the customer activation flow:
 * 1. Find customer by user ID
 * 2. Check if customer is already active (idempotency)
 * 3. Activate the customer profile
 * 4. Create CustomerActivated domain event
 * 5. Persist event to event store (within transaction)
 * 6. Persist updated customer (within transaction)
 * 7. Project to MongoDB (waits for completion before transaction commit)
 * 8. Write event to outbox (within transaction)
 *
 * The projection operation runs asynchronously but is awaited before the transaction
 * commits. The event is written to the outbox table within the same transaction,
 * and will be published to Kafka asynchronously by the OutboxRelay. This implements
 * the Transactional Outbox pattern to decouple Kafka availability from transaction success.
 */
@Service
class ActivateCustomerUseCase(
    private val customerRepository: CustomerRepository,
    private val customerPreferencesRepository: CustomerPreferencesRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val customerReadModelProjector: CustomerReadModelProjector,
    private val outboxWriter: OutboxWriter,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(ActivateCustomerUseCase::class.java)

    private val customerActivatedCounter: Counter = Counter.builder("customer.activated")
        .tag("status", "success")
        .register(meterRegistry)

    private val customerAlreadyActiveCounter: Counter = Counter.builder("customer.activated")
        .tag("status", "already_active")
        .register(meterRegistry)

    private val customerNotFoundCounter: Counter = Counter.builder("customer.activated")
        .tag("status", "not_found")
        .register(meterRegistry)

    private val customerActivationTimer: Timer = Timer.builder("customer.activation.duration")
        .register(meterRegistry)

    /**
     * Activates a customer profile based on user ID.
     *
     * @param userId The user ID from the Identity Service.
     * @param activatedAt When the activation occurred.
     * @param correlationId Correlation ID for distributed tracing.
     * @param causationId The event ID that caused this operation (UserActivated event).
     * @return The result of the operation.
     */
    @Transactional
    fun execute(
        userId: UUID,
        activatedAt: Instant,
        correlationId: UUID,
        causationId: UUID
    ): ActivateCustomerResult {
        return customerActivationTimer.record<ActivateCustomerResult> {
            logger.info("Activating customer profile for user {}", userId)

            // Find customer by user ID
            val customer = customerRepository.findByUserId(userId)
            if (customer == null) {
                logger.warn("No customer found for user {}", userId)
                customerNotFoundCounter.increment()
                return@record ActivateCustomerResult.CustomerNotFound(userId)
            }

            // Check if already active (idempotency)
            if (customer.isActive()) {
                logger.info(
                    "Customer {} is already active, skipping activation",
                    customer.id
                )
                customerAlreadyActiveCounter.increment()
                return@record ActivateCustomerResult.AlreadyActive(customer.id)
            }

            try {
                // Activate the customer
                customer.activate(activatedAt)

                // Create domain event
                val event = CustomerActivated.create(
                    customerId = customer.id,
                    activatedAt = activatedAt,
                    emailVerified = true,
                    correlationId = correlationId,
                    causationId = causationId
                )

                // Persist event to event store (within transaction)
                eventStoreRepository.append(event)

                // Persist updated customer (within transaction)
                customerRepository.save(customer)

                logger.info(
                    "Activated customer {} for user {}",
                    customer.id,
                    userId
                )

                // Get preferences for read model projection
                val preferences = customerPreferencesRepository.findById(customer.id)
                    .orElseThrow { IllegalStateException("Customer preferences not found for ${customer.id}") }

                // Project to MongoDB and write to outbox
                // Wait for projection to complete, then write to outbox
                val projectionFuture = customerReadModelProjector.projectCustomer(customer, preferences)

                // Wait for projection to complete before writing to outbox
                projectionFuture.join()

                // Write to outbox within the transaction
                // The OutboxRelay will publish asynchronously, decoupled from this transaction
                outboxWriter.write(event, CustomerActivated.TOPIC)

                customerActivatedCounter.increment()

                ActivateCustomerResult.Success(customer)
            } catch (e: IllegalStateException) {
                logger.error(
                    "Cannot activate customer {} for user {}: {}",
                    customer.id,
                    userId,
                    e.message,
                    e
                )
                ActivateCustomerResult.Failure(
                    message = "Cannot activate customer: ${e.message}",
                    cause = e
                )
            } catch (e: Exception) {
                logger.error(
                    "Failed to activate customer for user {}: {}",
                    userId,
                    e.message,
                    e
                )
                ActivateCustomerResult.Failure(
                    message = "Failed to activate customer: ${e.message}",
                    cause = e
                )
            }
        }!!
    }
}
