package com.acme.notification.application

import com.acme.notification.domain.NotificationDelivery
import com.acme.notification.domain.NotificationStatus
import com.acme.notification.domain.NotificationType
import com.acme.notification.domain.events.NotificationSent
import com.acme.notification.infrastructure.email.EmailSendResult
import com.acme.notification.infrastructure.email.SendGridEmailSender
import com.acme.notification.infrastructure.messaging.NotificationEventPublisher
import com.acme.notification.infrastructure.persistence.NotificationDeliveryRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

sealed class SendPasswordResetEmailResult {
    data class Success(val notificationId: UUID, val providerMessageId: String?) : SendPasswordResetEmailResult()
    data class Failure(val message: String, val cause: Throwable? = null) : SendPasswordResetEmailResult()
}

/**
 * Use case for sending password-reset emails. Mirrors
 * [SendVerificationEmailUseCase] but uses the password-reset template and
 * a unique notification id per request (multiple resets are expected over
 * an account's lifetime, so we do NOT short-circuit on prior deliveries).
 */
@Service
class SendPasswordResetEmailUseCase(
    private val emailSender: SendGridEmailSender,
    private val deliveryRepository: NotificationDeliveryRepository,
    private val eventPublisher: NotificationEventPublisher,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val emailSentCounter: Counter = Counter.builder("password_reset_email_sent_total")
        .tag("status", "success")
        .register(meterRegistry)

    private val emailFailedCounter: Counter = Counter.builder("password_reset_email_sent_total")
        .tag("status", "failed")
        .register(meterRegistry)

    private val emailDurationTimer: Timer = Timer.builder("password_reset_email_duration_seconds")
        .register(meterRegistry)

    @Transactional
    fun execute(
        userId: UUID,
        email: String,
        firstName: String,
        resetToken: String,
        correlationId: UUID
    ): SendPasswordResetEmailResult {
        return emailDurationTimer.record<SendPasswordResetEmailResult> {
            executeInternal(userId, email, firstName, resetToken, correlationId)
        } ?: SendPasswordResetEmailResult.Failure("Unexpected null result from timer")
    }

    private fun executeInternal(
        userId: UUID,
        email: String,
        firstName: String,
        resetToken: String,
        correlationId: UUID
    ): SendPasswordResetEmailResult {
        try {
            val notificationId = UUID.randomUUID()
            val delivery = NotificationDelivery(
                id = notificationId,
                notificationType = NotificationType.PASSWORD_RESET,
                recipientId = userId,
                recipientEmail = email,
                correlationId = correlationId.toString()
            )
            deliveryRepository.save(delivery)

            val result = emailSender.sendPasswordResetEmail(
                recipientEmail = email,
                recipientName = firstName,
                resetToken = resetToken,
                correlationId = correlationId.toString()
            )

            return when (result) {
                is EmailSendResult.Success -> {
                    delivery.markAsSent(result.messageId)
                    deliveryRepository.save(delivery)

                    val event = NotificationSent.create(
                        notificationId = notificationId,
                        type = NotificationType.PASSWORD_RESET,
                        recipientId = userId,
                        recipientEmail = email,
                        providerMessageId = result.messageId,
                        status = NotificationStatus.SENT,
                        correlationId = correlationId
                    )
                    eventPublisher.publish(event)

                    emailSentCounter.increment()
                    logger.info("Password reset email sent to {} for user {}", email, userId)

                    SendPasswordResetEmailResult.Success(notificationId, result.messageId)
                }
                is EmailSendResult.Failure -> {
                    delivery.markAsFailed()
                    deliveryRepository.save(delivery)
                    emailFailedCounter.increment()
                    logger.error(
                        "Failed to send password reset email to {} for user {}: {}",
                        email, userId, result.message
                    )
                    SendPasswordResetEmailResult.Failure(result.message, result.cause)
                }
            }
        } catch (e: Exception) {
            emailFailedCounter.increment()
            logger.error("Unexpected error sending password reset email to {}: {}", email, e.message, e)
            return SendPasswordResetEmailResult.Failure("Unexpected error: ${e.message}", e)
        }
    }
}
