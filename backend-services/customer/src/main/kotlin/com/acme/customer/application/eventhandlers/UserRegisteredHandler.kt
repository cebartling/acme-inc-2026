package com.acme.customer.application.eventhandlers

import com.acme.customer.application.CreateCustomerError
import com.acme.customer.application.CreateCustomerUseCase
import com.acme.customer.infrastructure.messaging.dto.UserRegisteredEvent
import com.acme.customer.infrastructure.persistence.ProcessedEvent
import com.acme.customer.infrastructure.persistence.ProcessedEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler for UserRegistered events from the Identity Service.
 *
 * This component bridges the Kafka consumer to the CreateCustomerUseCase,
 * translating the incoming event into a use case invocation.
 */
@Component
class UserRegisteredHandler(
    private val createCustomerUseCase: CreateCustomerUseCase,
    private val processedEventRepository: ProcessedEventRepository
) {
    private val logger = LoggerFactory.getLogger(UserRegisteredHandler::class.java)

    /**
     * Handles a UserRegistered event by creating a customer profile.
     *
     * This method performs the idempotency check and marks the event as processed
     * within the same transaction as customer creation to prevent race conditions.
     *
     * @param event The UserRegistered event from Kafka.
     * @throws RuntimeException If customer creation fails.
     */
    @Transactional
    fun handle(event: UserRegisteredEvent) {
        logger.info(
            "Handling UserRegistered event {} for user {}",
            event.eventId,
            event.payload.userId
        )

        // Idempotency check within transaction
        if (processedEventRepository.existsByEventId(event.eventId)) {
            logger.info(
                "Event {} already processed, skipping (user: {})",
                event.eventId,
                event.payload.userId
            )
            return
        }

        createCustomerUseCase.execute(
            userId = event.payload.userId,
            email = event.payload.email,
            firstName = event.payload.firstName,
            lastName = event.payload.lastName,
            marketingOptIn = event.payload.marketingOptIn,
            registeredAt = event.timestamp,
            correlationId = event.correlationId,
            causationId = event.eventId
        ).fold(
            ifLeft = { error ->
                when (error) {
                    is CreateCustomerError.AlreadyExists -> {
                        logger.info(
                            "Customer {} already exists for user {}, event processed idempotently",
                            error.existingCustomerId,
                            error.userId
                        )
                    }
                    is CreateCustomerError.Failure -> {
                        logger.error(
                            "Failed to create customer for user {}: {}",
                            event.payload.userId,
                            error.message,
                            error.cause
                        )
                        throw RuntimeException(error.message, error.cause)
                    }
                }
            },
            ifRight = { success ->
                logger.info(
                    "Created customer {} with number {} for user {}",
                    success.customer.id,
                    success.customer.customerNumber,
                    event.payload.userId
                )
            }
        )

        // Mark event as processed within same transaction (after successful processing)
        processedEventRepository.save(
            ProcessedEvent(
                eventId = event.eventId,
                eventType = event.eventType
            )
        )
    }
}
