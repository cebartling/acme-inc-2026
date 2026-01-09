package com.acme.notification.infrastructure.email

import com.acme.notification.infrastructure.templates.EmailTemplateService
import com.sendgrid.Method
import com.sendgrid.Request
import com.sendgrid.SendGrid
import com.sendgrid.helpers.mail.Mail
import com.sendgrid.helpers.mail.objects.Content
import com.sendgrid.helpers.mail.objects.Email
import com.sendgrid.helpers.mail.objects.Personalization
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * SendGrid email sender for sending transactional and marketing emails.
 *
 * Integrates with SendGrid API v3 to send emails including:
 * - Verification emails
 * - Welcome emails (transactional and marketing variants)
 */
@Service
class SendGridEmailSender(
    private val sendGrid: SendGrid,
    private val templateService: EmailTemplateService,
    @Value("\${notification.email.from-address}")
    private val fromAddress: String,
    @Value("\${notification.email.from-name}")
    private val fromName: String,
    @Value("\${notification.email.verification.subject}")
    private val verificationSubject: String,
    @Value("\${notification.email.verification.base-url}")
    private val verificationBaseUrl: String,
    @Value("\${notification.email.verification.expiration-hours}")
    private val expirationHours: Int,
    @Value("\${notification.email.support-email}")
    private val supportEmail: String,
    @Value("\${notification.email.company-name}")
    private val companyName: String,
    @Value("\${sendgrid.sandbox-mode:false}")
    private val sandboxMode: Boolean,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(SendGridEmailSender::class.java)

    private val verificationEmailSentCounter: Counter = Counter.builder("email_delivery_status_total")
        .tag("type", "EMAIL_VERIFICATION")
        .tag("status", "sent")
        .register(meterRegistry)

    private val verificationEmailFailedCounter: Counter = Counter.builder("email_delivery_status_total")
        .tag("type", "EMAIL_VERIFICATION")
        .tag("status", "failed")
        .register(meterRegistry)

    private val welcomeEmailSentCounter: Counter = Counter.builder("email_delivery_status_total")
        .tag("type", "WELCOME")
        .tag("status", "sent")
        .register(meterRegistry)

    private val welcomeEmailFailedCounter: Counter = Counter.builder("email_delivery_status_total")
        .tag("type", "WELCOME")
        .tag("status", "failed")
        .register(meterRegistry)

    /**
     * Sends a verification email to the specified recipient.
     *
     * @param recipientEmail The recipient's email address.
     * @param recipientName The recipient's first name.
     * @param verificationToken The verification token.
     * @param correlationId The correlation ID for tracing.
     * @return The result of the send operation.
     */
    fun sendVerificationEmail(
        recipientEmail: String,
        recipientName: String,
        verificationToken: String,
        correlationId: String
    ): EmailSendResult {
        try {
            // Build verification URL
            val verificationUrl = "$verificationBaseUrl?token=$verificationToken"

            // Render email template
            val htmlContent = templateService.renderVerificationEmail(
                recipientName = recipientName,
                verificationUrl = verificationUrl,
                expirationHours = expirationHours,
                supportEmail = supportEmail,
                companyName = companyName
            )

            // In sandbox mode, simulate successful send without calling SendGrid API
            if (this.sandboxMode) {
                val simulatedMessageId = "sandbox-${java.util.UUID.randomUUID()}"
                verificationEmailSentCounter.increment()
                logger.info(
                    "Verification email simulated (sandbox mode) to {} with message ID {}",
                    recipientEmail,
                    simulatedMessageId
                )
                return EmailSendResult.Success(simulatedMessageId, 202)
            }

            // Build email
            val mail = Mail().apply {
                from = Email(fromAddress, fromName)
                subject = verificationSubject
                addPersonalization(Personalization().apply {
                    addTo(Email(recipientEmail, recipientName))
                })
                addContent(Content("text/html", htmlContent))
                addHeader("X-Correlation-ID", correlationId)
            }

            // Send email
            val request = Request().apply {
                method = Method.POST
                endpoint = "mail/send"
                body = mail.build()
            }

            val response = sendGrid.api(request)

            return if (response.statusCode in 200..299) {
                val messageId = response.headers["X-Message-Id"]
                verificationEmailSentCounter.increment()
                logger.info(
                    "Verification email sent to {} with message ID {} (status: {})",
                    recipientEmail,
                    messageId,
                    response.statusCode
                )
                EmailSendResult.Success(messageId, response.statusCode)
            } else {
                verificationEmailFailedCounter.increment()
                logger.error(
                    "Failed to send verification email to {}: status={}, body={}",
                    recipientEmail,
                    response.statusCode,
                    response.body
                )
                EmailSendResult.Failure(
                    "SendGrid returned status ${response.statusCode}: ${response.body}",
                    response.statusCode
                )
            }
        } catch (e: Exception) {
            verificationEmailFailedCounter.increment()
            logger.error(
                "Exception sending verification email to {}: {}",
                recipientEmail,
                e.message,
                e
            )
            return EmailSendResult.Failure("Failed to send email: ${e.message}", cause = e)
        }
    }

    /**
     * Sends a welcome email to the specified recipient.
     *
     * The email content varies based on marketing preference:
     * - If marketingOptIn is true, includes promotional content with unsubscribe link
     * - If marketingOptIn is false, sends transactional-only welcome message
     *
     * @param recipientEmail The recipient's email address.
     * @param recipientName The recipient's first name.
     * @param displayName The recipient's display name.
     * @param customerNumber The customer number for reference.
     * @param marketingOptIn Whether the customer opted in to marketing.
     * @param showProfileCta Whether to show the profile completion CTA.
     * @param correlationId The correlation ID for tracing.
     * @return The result of the send operation.
     */
    fun sendWelcomeEmail(
        recipientEmail: String,
        recipientName: String,
        displayName: String,
        customerNumber: String,
        marketingOptIn: Boolean,
        showProfileCta: Boolean,
        correlationId: String
    ): EmailSendResult {
        try {
            // Render email template based on marketing preference
            val htmlContent = templateService.renderWelcomeEmail(
                recipientName = recipientName,
                displayName = displayName,
                customerNumber = customerNumber,
                marketingOptIn = marketingOptIn,
                showProfileCta = showProfileCta
            )

            // Build subject line with personalized greeting
            val subject = "Welcome to $companyName, $recipientName!"

            // Determine email category based on marketing preference
            val category = if (marketingOptIn) "marketing" else "transactional"

            // In sandbox mode, simulate successful send without calling SendGrid API
            if (this.sandboxMode) {
                val simulatedMessageId = "sandbox-${java.util.UUID.randomUUID()}"
                welcomeEmailSentCounter.increment()
                logger.info(
                    "Welcome email simulated (sandbox mode) to {} with message ID {} (category={})",
                    recipientEmail,
                    simulatedMessageId,
                    category
                )
                return EmailSendResult.Success(simulatedMessageId, 202)
            }

            // Build email
            val mail = Mail().apply {
                from = Email(fromAddress, fromName)
                this.subject = subject
                addPersonalization(Personalization().apply {
                    addTo(Email(recipientEmail, displayName))
                })
                addContent(Content("text/html", htmlContent))
                addHeader("X-Correlation-ID", correlationId)
                addCategory(category)
            }

            // Send email
            val request = Request().apply {
                method = Method.POST
                endpoint = "mail/send"
                body = mail.build()
            }

            val response = sendGrid.api(request)

            return if (response.statusCode in 200..299) {
                val messageId = response.headers["X-Message-Id"]
                welcomeEmailSentCounter.increment()
                logger.info(
                    "Welcome email sent to {} with message ID {} (status: {}, category={})",
                    recipientEmail,
                    messageId,
                    response.statusCode,
                    category
                )
                EmailSendResult.Success(messageId, response.statusCode)
            } else {
                welcomeEmailFailedCounter.increment()
                logger.error(
                    "Failed to send welcome email to {}: status={}, body={}",
                    recipientEmail,
                    response.statusCode,
                    response.body
                )
                EmailSendResult.Failure(
                    "SendGrid returned status ${response.statusCode}: ${response.body}",
                    response.statusCode
                )
            }
        } catch (e: Exception) {
            welcomeEmailFailedCounter.increment()
            logger.error(
                "Exception sending welcome email to {}: {}",
                recipientEmail,
                e.message,
                e
            )
            return EmailSendResult.Failure("Failed to send email: ${e.message}", cause = e)
        }
    }
}
