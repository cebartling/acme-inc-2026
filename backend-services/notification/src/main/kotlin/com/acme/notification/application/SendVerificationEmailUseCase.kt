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

/**
 * Sealed class representing the possible outcomes of sending a verification email.
 */
sealed class SendVerificationEmailResult {
    /**
     * Email was sent successfully.
     */
    data class Success(
        val notificationId: UUID,
        val providerMessageId: String?
    ) : SendVerificationEmailResult()

    /**
     * Email sending failed.
     */
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : SendVerificationEmailResult()

    /**
     * Email was already sent (idempotency).
     */
    data class AlreadySent(
        val notificationId: UUID
    ) : SendVerificationEmailResult()
}

/**
 * Use case for sending verification emails.
 *
 * Orchestrates the process of:
 * - Checking if email was already sent (idempotency)
 * - Rendering the email template
 * - Sending via SendGrid
 * - Recording delivery status
 * - Publishing NotificationSent event
 */
@Service
class SendVerificationEmailUseCase(
    private val emailSender: SendGridEmailSender,
    private val deliveryRepository: NotificationDeliveryRepository,
    private val eventPublisher: NotificationEventPublisher,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(SendVerificationEmailUseCase::class.java)

    private val emailSentCounter: Counter = Counter.builder("verification_email_sent_total")
        .tag("status", "success")
        .register(meterRegistry)

    private val emailFailedCounter: Counter = Counter.builder("verification_email_sent_total")
        .tag("status", "failed")
        .register(meterRegistry)

    private val emailDurationTimer: Timer = Timer.builder("verification_email_duration_seconds")
        .register(meterRegistry)

    /**
     * Sends a verification email to the user.
     *
     * @param userId The user ID.
     * @param email The user's email address.
     * @param firstName The user's first name.
     * @param verificationToken The verification token.
     * @param correlationId The correlation ID for distributed tracing.
     * @return The result of the send operation.
     */
    @Transactional
    fun execute(
        userId: UUID,
        email: String,
        firstName: String,
        verificationToken: String,
        correlationId: UUID
    ): SendVerificationEmailResult {
        return emailDurationTimer.record<SendVerificationEmailResult> {
            executeInternal(userId, email, firstName, verificationToken, correlationId)
        } ?: SendVerificationEmailResult.Failure("Unexpected null result from timer")
    }

    private fun executeInternal(
        userId: UUID,
        email: String,
        firstName: String,
        verificationToken: String,
        correlationId: UUID
    ): SendVerificationEmailResult {
        try {
            // Check if verification email was already sent for this user
            if (deliveryRepository.existsByRecipientIdAndNotificationType(userId, NotificationType.EMAIL_VERIFICATION)) {
                logger.info("Verification email already sent for user {}, skipping", userId)
                val existing = deliveryRepository.findByRecipientId(userId)
                    .firstOrNull { it.notificationType == NotificationType.EMAIL_VERIFICATION }
                return SendVerificationEmailResult.AlreadySent(existing?.id ?: userId)
            }

            // Create delivery record
            val notificationId = UUID.randomUUID()
            val delivery = NotificationDelivery(
                id = notificationId,
                notificationType = NotificationType.EMAIL_VERIFICATION,
                recipientId = userId,
                recipientEmail = email,
                correlationId = correlationId.toString()
            )
            deliveryRepository.save(delivery)

            // Send email
            val result = emailSender.sendVerificationEmail(
                recipientEmail = email,
                recipientName = firstName,
                verificationToken = verificationToken,
                correlationId = correlationId.toString()
            )

            when (result) {
                is EmailSendResult.Success -> {
                    delivery.markAsSent(result.messageId)
                    deliveryRepository.save(delivery)

                    // Publish NotificationSent event
                    val event = NotificationSent.create(
                        notificationId = notificationId,
                        type = NotificationType.EMAIL_VERIFICATION,
                        recipientId = userId,
                        recipientEmail = email,
                        providerMessageId = result.messageId,
                        status = NotificationStatus.SENT,
                        correlationId = correlationId
                    )
                    eventPublisher.publish(event)

                    emailSentCounter.increment()
                    logger.info(
                        "Verification email sent successfully to {} for user {}",
                        email,
                        userId
                    )

                    return SendVerificationEmailResult.Success(notificationId, result.messageId)
                }

                is EmailSendResult.Failure -> {
                    delivery.markAsFailed()
                    deliveryRepository.save(delivery)

                    emailFailedCounter.increment()
                    logger.error(
                        "Failed to send verification email to {} for user {}: {}",
                        email,
                        userId,
                        result.message
                    )

                    return SendVerificationEmailResult.Failure(result.message, result.cause)
                }
            }
        } catch (e: Exception) {
            emailFailedCounter.increment()
            logger.error(
                "Unexpected error sending verification email to {}: {}",
                email,
                e.message,
                e
            )
            return SendVerificationEmailResult.Failure("Unexpected error: ${e.message}", e)
        }
    }
}
