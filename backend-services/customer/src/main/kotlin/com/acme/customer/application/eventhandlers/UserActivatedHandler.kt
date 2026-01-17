package com.acme.customer.application.eventhandlers

import com.acme.customer.application.ActivateCustomerError
import com.acme.customer.application.ActivateCustomerUseCase
import com.acme.customer.infrastructure.messaging.dto.UserActivatedEvent
import com.acme.customer.infrastructure.persistence.ProcessedEvent
import com.acme.customer.infrastructure.persistence.ProcessedEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler for UserActivated events from the Identity Service.
 *
 * This component bridges the Kafka consumer to the ActivateCustomerUseCase,
 * translating the incoming event into a use case invocation.
 */
@Component
class UserActivatedHandler(
    private val activateCustomerUseCase: ActivateCustomerUseCase,
    private val processedEventRepository: ProcessedEventRepository
) {
    private val logger = LoggerFactory.getLogger(UserActivatedHandler::class.java)

    /**
     * Handles a UserActivated event by activating the customer profile.
     *
     * This method performs the idempotency check and marks the event as processed
     * within the same transaction as customer activation to prevent race conditions.
     *
     * @param event The UserActivated event from Kafka.
     * @throws RuntimeException If customer activation fails.
     */
    @Transactional
    fun handle(event: UserActivatedEvent) {
        logger.info(
            "Handling UserActivated event {} for user {}",
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

        activateCustomerUseCase.execute(
            userId = event.payload.userId,
            activatedAt = event.payload.activatedAt,
            correlationId = event.correlationId,
            causationId = event.eventId
        ).fold(
            ifLeft = { error ->
                when (error) {
                    is ActivateCustomerError.AlreadyActive -> {
                        logger.info(
                            "Customer {} already active for user {}, event processed idempotently",
                            error.customerId,
                            event.payload.userId
                        )
                    }
                    is ActivateCustomerError.CustomerNotFound -> {
                        logger.warn(
                            "No customer found for user {}, cannot activate",
                            error.userId
                        )
                        // This is not necessarily an error - the customer profile might not have been
                        // created yet due to event ordering. The event will be retried.
                        throw RuntimeException("Customer not found for user ${error.userId}")
                    }
                    is ActivateCustomerError.Failure -> {
                        logger.error(
                            "Failed to activate customer for user {}: {}",
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
                    "Activated customer {} for user {}",
                    success.customer.id,
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
