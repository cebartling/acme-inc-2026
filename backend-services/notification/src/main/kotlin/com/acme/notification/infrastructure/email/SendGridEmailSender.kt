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
 * SendGrid email sender for sending verification emails.
 *
 * Integrates with SendGrid API v3 to send transactional emails.
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

    private val emailSentCounter: Counter = Counter.builder("email_delivery_status_total")
        .tag("type", "EMAIL_VERIFICATION")
        .tag("status", "sent")
        .register(meterRegistry)

    private val emailFailedCounter: Counter = Counter.builder("email_delivery_status_total")
        .tag("type", "EMAIL_VERIFICATION")
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
                recipientEmail = recipientEmail,
                verificationUrl = verificationUrl,
                expirationHours = expirationHours,
                supportEmail = supportEmail,
                companyName = companyName
            )

            // Build email
            val mail = Mail().apply {
                from = Email(fromAddress, fromName)
                subject = verificationSubject
                addPersonalization(Personalization().apply {
                    addTo(Email(recipientEmail, recipientName))
                })
                addContent(Content("text/html", htmlContent))
                addHeader("X-Correlation-ID", correlationId)

                // Enable sandbox mode for testing
                if (this@SendGridEmailSender.sandboxMode) {
                    val sandboxSetting = com.sendgrid.helpers.mail.objects.Setting().apply {
                        enable = true
                    }
                    mailSettings = com.sendgrid.helpers.mail.objects.MailSettings().apply {
                        setSandboxMode(sandboxSetting)
                    }
                }
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
                emailSentCounter.increment()
                logger.info(
                    "Verification email sent to {} with message ID {} (status: {})",
                    recipientEmail,
                    messageId,
                    response.statusCode
                )
                EmailSendResult.Success(messageId, response.statusCode)
            } else {
                emailFailedCounter.increment()
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
            emailFailedCounter.increment()
            logger.error(
                "Exception sending verification email to {}: {}",
                recipientEmail,
                e.message,
                e
            )
            return EmailSendResult.Failure("Failed to send email: ${e.message}", cause = e)
        }
    }
}
