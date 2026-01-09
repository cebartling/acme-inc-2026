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
 * Sealed class representing the possible outcomes of sending a welcome email.
 */
sealed class SendWelcomeEmailResult {
    /**
     * Email was sent successfully.
     */
    data class Success(
        val notificationId: UUID,
        val providerMessageId: String?,
        val marketingIncluded: Boolean
    ) : SendWelcomeEmailResult()

    /**
     * Email sending failed.
     */
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : SendWelcomeEmailResult()

    /**
     * Email was already sent (idempotency).
     */
    data class AlreadySent(
        val notificationId: UUID
    ) : SendWelcomeEmailResult()
}

/**
 * Use case for sending welcome emails.
 *
 * Orchestrates the process of:
 * - Checking if email was already sent (idempotency)
 * - Selecting appropriate template based on marketing preference
 * - Rendering the email template
 * - Sending via SendGrid
 * - Recording delivery status
 * - Publishing NotificationSent event
 */
@Service
class SendWelcomeEmailUseCase(
    private val emailSender: SendGridEmailSender,
    private val deliveryRepository: NotificationDeliveryRepository,
    private val eventPublisher: NotificationEventPublisher,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(SendWelcomeEmailUseCase::class.java)

    private val emailSentCounter: Counter = Counter.builder("notification.welcome_email.sent")
        .tag("status", "success")
        .register(meterRegistry)

    private val emailSentWithMarketingCounter: Counter = Counter.builder("notification.welcome_email.sent")
        .tag("status", "success")
        .tag("marketing_included", "true")
        .register(meterRegistry)

    private val emailSentWithoutMarketingCounter: Counter = Counter.builder("notification.welcome_email.sent")
        .tag("status", "success")
        .tag("marketing_included", "false")
        .register(meterRegistry)

    private val emailFailedCounter: Counter = Counter.builder("notification.welcome_email.sent")
        .tag("status", "failed")
        .register(meterRegistry)

    private val emailDurationTimer: Timer = Timer.builder("notification.welcome_email.duration")
        .register(meterRegistry)

    /**
     * Sends a welcome email to the customer.
     *
     * @param customerId The customer ID.
     * @param email The customer's email address.
     * @param firstName The customer's first name.
     * @param displayName The customer's display name.
     * @param customerNumber The customer number for reference.
     * @param marketingOptIn Whether the customer opted in to marketing.
     * @param profileCompleteness The profile completeness percentage.
     * @param correlationId The correlation ID for distributed tracing.
     * @return The result of the send operation.
     */
    @Transactional
    fun execute(
        customerId: UUID,
        email: String,
        firstName: String,
        displayName: String,
        customerNumber: String,
        marketingOptIn: Boolean,
        profileCompleteness: Int,
        correlationId: UUID
    ): SendWelcomeEmailResult {
        return emailDurationTimer.record<SendWelcomeEmailResult> {
            executeInternal(
                customerId = customerId,
                email = email,
                firstName = firstName,
                displayName = displayName,
                customerNumber = customerNumber,
                marketingOptIn = marketingOptIn,
                profileCompleteness = profileCompleteness,
                correlationId = correlationId
            )
        } ?: SendWelcomeEmailResult.Failure("Unexpected null result from timer")
    }

    private fun executeInternal(
        customerId: UUID,
        email: String,
        firstName: String,
        displayName: String,
        customerNumber: String,
        marketingOptIn: Boolean,
        profileCompleteness: Int,
        correlationId: UUID
    ): SendWelcomeEmailResult {
        try {
            // Check if welcome email was already sent for this customer
            if (deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME)) {
                logger.info("Welcome email already sent for customer {}, skipping", customerId)
                val existing = deliveryRepository.findByRecipientId(customerId)
                    .firstOrNull { it.notificationType == NotificationType.WELCOME }

                if (existing == null) {
                    logger.error(
                        "Inconsistent state detected: welcome notification exists for customer {} " +
                            "but corresponding delivery record could not be retrieved",
                        customerId
                    )
                    throw IllegalStateException(
                        "Inconsistent notification delivery state for customer $customerId"
                    )
                }

                return SendWelcomeEmailResult.AlreadySent(existing.id)
            }

            // Create delivery record
            val notificationId = UUID.randomUUID()
            val delivery = NotificationDelivery(
                id = notificationId,
                notificationType = NotificationType.WELCOME,
                recipientId = customerId,
                recipientEmail = email,
                correlationId = correlationId.toString()
            )
            deliveryRepository.save(delivery)

            // Send email with appropriate template based on marketing preference
            val result = emailSender.sendWelcomeEmail(
                recipientEmail = email,
                recipientName = firstName,
                displayName = displayName,
                customerNumber = customerNumber,
                marketingOptIn = marketingOptIn,
                showProfileCta = profileCompleteness < 100,
                correlationId = correlationId.toString()
            )

            when (result) {
                is EmailSendResult.Success -> {
                    delivery.markAsSent(result.messageId)
                    deliveryRepository.save(delivery)

                    // Publish NotificationSent event
                    val event = NotificationSent.create(
                        notificationId = notificationId,
                        type = NotificationType.WELCOME,
                        recipientId = customerId,
                        recipientEmail = email,
                        providerMessageId = result.messageId,
                        status = NotificationStatus.SENT,
                        correlationId = correlationId
                    )
                    eventPublisher.publish(event)

                    // Update metrics
                    emailSentCounter.increment()
                    if (marketingOptIn) {
                        emailSentWithMarketingCounter.increment()
                    } else {
                        emailSentWithoutMarketingCounter.increment()
                    }

                    logger.info(
                        "Welcome email sent successfully to {} for customer {} (marketing={})",
                        email,
                        customerId,
                        marketingOptIn
                    )

                    return SendWelcomeEmailResult.Success(
                        notificationId = notificationId,
                        providerMessageId = result.messageId,
                        marketingIncluded = marketingOptIn
                    )
                }

                is EmailSendResult.Failure -> {
                    delivery.markAsFailed()
                    deliveryRepository.save(delivery)

                    emailFailedCounter.increment()
                    logger.error(
                        "Failed to send welcome email to {} for customer {}: {}",
                        email,
                        customerId,
                        result.message
                    )

                    return SendWelcomeEmailResult.Failure(result.message, result.cause)
                }
            }
        } catch (e: Exception) {
            emailFailedCounter.increment()
            logger.error(
                "Unexpected error sending welcome email to {}: {}",
                email,
                e.message,
                e
            )
            return SendWelcomeEmailResult.Failure("Unexpected error: ${e.message}", e)
        }
    }
}
