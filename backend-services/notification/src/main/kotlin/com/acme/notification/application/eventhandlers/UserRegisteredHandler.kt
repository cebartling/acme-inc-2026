package com.acme.notification.application.eventhandlers

import com.acme.notification.application.SendVerificationEmailResult
import com.acme.notification.application.SendVerificationEmailUseCase
import com.acme.notification.infrastructure.messaging.dto.UserRegisteredEvent
import com.acme.notification.infrastructure.persistence.ProcessedEvent
import com.acme.notification.infrastructure.persistence.ProcessedEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler for UserRegistered events from the Identity Service.
 *
 * This component processes incoming UserRegistered events and
 * triggers the sending of verification emails.
 */
@Component
class UserRegisteredHandler(
    private val sendVerificationEmailUseCase: SendVerificationEmailUseCase,
    private val processedEventRepository: ProcessedEventRepository
) {
    private val logger = LoggerFactory.getLogger(UserRegisteredHandler::class.java)

    /**
     * Handles a UserRegistered event by sending a verification email.
     *
     * This method performs the idempotency check and marks the event as processed
     * within the same transaction to prevent race conditions.
     *
     * @param event The UserRegistered event from Kafka.
     * @throws RuntimeException If email sending fails.
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

        // Validate that verification token is present
        val verificationToken = event.payload.verificationToken
        if (verificationToken.isNullOrBlank()) {
            logger.warn(
                "UserRegistered event {} for user {} missing verification token, skipping email",
                event.eventId,
                event.payload.userId
            )
            // Mark as processed to avoid retrying legacy events without tokens
            processedEventRepository.save(
                ProcessedEvent(
                    eventId = event.eventId,
                    eventType = event.eventType
                )
            )
            return
        }

        val result = sendVerificationEmailUseCase.execute(
            userId = event.payload.userId,
            email = event.payload.email,
            firstName = event.payload.firstName,
            verificationToken = verificationToken,
            correlationId = event.correlationId
        )

        when (result) {
            is SendVerificationEmailResult.Success -> {
                logger.info(
                    "Sent verification email to {} for user {}, notification ID: {}",
                    event.payload.email,
                    event.payload.userId,
                    result.notificationId
                )
            }

            is SendVerificationEmailResult.AlreadySent -> {
                logger.info(
                    "Verification email already sent for user {}, notification ID: {}",
                    event.payload.userId,
                    result.notificationId
                )
            }

            is SendVerificationEmailResult.Failure -> {
                logger.error(
                    "Failed to send verification email to {} for user {}: {}",
                    event.payload.email,
                    event.payload.userId,
                    result.message,
                    result.cause
                )
                throw RuntimeException(result.message, result.cause)
            }
        }

        // Mark event as processed within same transaction
        processedEventRepository.save(
            ProcessedEvent(
                eventId = event.eventId,
                eventType = event.eventType
            )
        )
    }
}
