package com.acme.notification.application.eventhandlers

import com.acme.notification.application.SendPasswordResetEmailResult
import com.acme.notification.application.SendPasswordResetEmailUseCase
import com.acme.notification.infrastructure.messaging.dto.PasswordResetRequestedEvent
import com.acme.notification.infrastructure.persistence.ProcessedEvent
import com.acme.notification.infrastructure.persistence.ProcessedEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PasswordResetRequestedHandler(
    private val sendPasswordResetEmailUseCase: SendPasswordResetEmailUseCase,
    private val processedEventRepository: ProcessedEventRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(event: PasswordResetRequestedEvent) {
        logger.info(
            "Handling PasswordResetRequested event {} for user {}",
            event.eventId, event.payload.userId
        )

        if (processedEventRepository.existsByEventId(event.eventId)) {
            logger.info(
                "Event {} already processed, skipping (user: {})",
                event.eventId, event.payload.userId
            )
            return
        }

        val result = sendPasswordResetEmailUseCase.execute(
            userId = event.payload.userId,
            email = event.payload.email,
            firstName = event.payload.firstName,
            resetToken = event.payload.resetToken,
            correlationId = event.correlationId
        )

        when (result) {
            is SendPasswordResetEmailResult.Success ->
                logger.info(
                    "Sent password reset email to {} for user {}, notification ID: {}",
                    event.payload.email, event.payload.userId, result.notificationId
                )
            is SendPasswordResetEmailResult.Failure -> {
                logger.error(
                    "Failed to send password reset email to {} for user {}: {}",
                    event.payload.email, event.payload.userId, result.message, result.cause
                )
                throw RuntimeException(result.message, result.cause)
            }
        }

        processedEventRepository.save(
            ProcessedEvent(eventId = event.eventId, eventType = event.eventType)
        )
    }
}
