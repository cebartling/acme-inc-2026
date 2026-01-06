package com.acme.customer.application

import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerNumber
import com.acme.customer.domain.CustomerPreferences
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import com.acme.customer.domain.events.CustomerRegistered
import com.acme.customer.infrastructure.messaging.CustomerEventPublisher
import com.acme.customer.infrastructure.persistence.CustomerNumberSequenceRepository
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.persistence.EventStoreRepository
import com.acme.customer.infrastructure.projection.CustomerReadModelProjector
import com.acme.customer.infrastructure.security.CustomerIdGenerator
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/**
 * Use case for creating a new customer profile.
 *
 * This use case orchestrates the customer creation flow:
 * 1. Check if customer already exists for user ID
 * 2. Generate UUID v7 customer ID
 * 3. Generate customer number (ACME-YYYYMM-NNNNNN)
 * 4. Create customer and preferences entities
 * 5. Persist event to event store (within transaction)
 * 6. Persist customer and preferences (within transaction)
 * 7. Publish CustomerRegistered event to Kafka (synchronous, within transaction)
 * 8. Project to MongoDB (async, after transaction commits)
 */
@Service
class CreateCustomerUseCase(
    private val customerRepository: CustomerRepository,
    private val customerPreferencesRepository: CustomerPreferencesRepository,
    private val customerNumberSequenceRepository: CustomerNumberSequenceRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val customerIdGenerator: CustomerIdGenerator,
    private val customerReadModelProjector: CustomerReadModelProjector,
    private val customerEventPublisher: CustomerEventPublisher,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(CreateCustomerUseCase::class.java)

    private val customerCreatedCounter: Counter = Counter.builder("customer.created")
        .tag("status", "success")
        .register(meterRegistry)

    private val customerExistsCounter: Counter = Counter.builder("customer.created")
        .tag("status", "already_exists")
        .register(meterRegistry)

    private val customerCreationTimer: Timer = Timer.builder("customer.creation.duration")
        .register(meterRegistry)

    /**
     * Creates a new customer profile from user registration data.
     *
     * @param userId The user ID from the Identity Service.
     * @param email The user's email address.
     * @param firstName The user's first name.
     * @param lastName The user's last name.
     * @param marketingOptIn Whether the user opted in to marketing.
     * @param registeredAt When the user registered.
     * @param correlationId Correlation ID for distributed tracing.
     * @param causationId The event ID that caused this operation.
     * @return The result of the operation.
     */
    @Transactional
    fun execute(
        userId: UUID,
        email: String,
        firstName: String,
        lastName: String,
        marketingOptIn: Boolean,
        registeredAt: Instant,
        correlationId: UUID,
        causationId: UUID
    ): CreateCustomerResult {
        return customerCreationTimer.record<CreateCustomerResult> {
            logger.info("Creating customer profile for user {}", userId)

            // Check if customer already exists
            customerRepository.findByUserId(userId)?.let { existingCustomer ->
                logger.info(
                    "Customer {} already exists for user {}, skipping creation",
                    existingCustomer.id,
                    userId
                )
                customerExistsCounter.increment()
                return@record CreateCustomerResult.AlreadyExists(
                    userId = userId,
                    existingCustomerId = existingCustomer.id
                )
            }

            try {
                // Generate customer ID (UUID v7)
                val customerId = customerIdGenerator.generate()

                // Generate customer number
                val yearMonth = YearMonth.now()
                val sequence = customerNumberSequenceRepository.nextSequence(yearMonth)
                val customerNumber = CustomerNumber.create(yearMonth, sequence)

                logger.debug(
                    "Generated customer ID {} and number {} for user {}",
                    customerId,
                    customerNumber,
                    userId
                )

                // Create customer entity
                val customer = Customer.createFromRegistration(
                    id = customerId,
                    userId = userId,
                    customerNumber = customerNumber.value,
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    registeredAt = registeredAt
                )

                // Create preferences with marketing opt-in
                val preferences = CustomerPreferences.createDefault(
                    customerId = customerId,
                    marketingOptIn = marketingOptIn
                )

                // Create domain event
                val event = CustomerRegistered.create(
                    customerId = customerId,
                    userId = userId,
                    customerNumber = customerNumber.value,
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    status = CustomerStatus.PENDING_VERIFICATION,
                    type = CustomerType.INDIVIDUAL,
                    registeredAt = registeredAt,
                    correlationId = correlationId,
                    causationId = causationId
                )

                // Persist event to event store (within transaction)
                eventStoreRepository.append(event)

                // Persist customer and preferences (within transaction)
                customerRepository.save(customer)
                customerPreferencesRepository.save(preferences)

                // Publish event to Kafka (synchronous, within transaction)
                // This ensures that if Kafka publishing fails, the transaction will be rolled back
                customerEventPublisher.publish(event)

                logger.info(
                    "Created customer {} with number {} for user {}",
                    customerId,
                    customerNumber,
                    userId
                )

                // Project to MongoDB (async, outside transaction after commit)
                customerReadModelProjector.projectCustomer(customer, preferences)

                customerCreatedCounter.increment()

                CreateCustomerResult.Success(
                    customer = customer,
                    preferences = preferences
                )
            } catch (e: DataIntegrityViolationException) {
                // Handle race condition: another instance created the customer between our check and save
                // The transaction has already rolled back at this point, so we're outside the transaction scope
                logger.warn(
                    "Constraint violation when creating customer for user {}, checking if customer exists",
                    userId,
                    e
                )
                
                // Re-check if customer exists after constraint violation
                // Note: This is a best-effort check outside the transaction scope.
                // If another instance created the customer, we should find it here.
                customerRepository.findByUserId(userId)?.let { existingCustomer ->
                    logger.info(
                        "Customer {} was created by another instance for user {}, returning existing customer",
                        existingCustomer.id,
                        userId
                    )
                    customerExistsCounter.increment()
                    return@record CreateCustomerResult.AlreadyExists(
                        userId = userId,
                        existingCustomerId = existingCustomer.id
                    )
                } ?: run {
                    // Constraint violation but customer still doesn't exist - unexpected
                    logger.error(
                        "Constraint violation for user {} but customer not found",
                        userId,
                        e
                    )
                    return@record CreateCustomerResult.Failure(
                        message = "Database constraint violation: ${e.message}",
                        cause = e
                    )
                }
            } catch (e: Exception) {
                logger.error(
                    "Failed to create customer for user {}: {}",
                    userId,
                    e.message,
                    e
                )
                CreateCustomerResult.Failure(
                    message = "Failed to create customer: ${e.message}",
                    cause = e
                )
            }
        }!!
    }
}
