package com.acme.identity.infrastructure.sms

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.twilio.Twilio
import com.twilio.exception.ApiException
import com.twilio.rest.api.v2010.account.Message
import com.twilio.type.PhoneNumber
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct

/**
 * Twilio implementation of [SmsProvider].
 *
 * Sends SMS messages via the Twilio REST API.
 * Thread-safe and suitable for concurrent use.
 */
@Component
@ConditionalOnProperty(name = ["identity.sms.provider"], havingValue = "twilio")
class TwilioSmsProvider(
    @Value("\${identity.sms.twilio.account-sid}")
    private val accountSid: String,

    @Value("\${identity.sms.twilio.auth-token}")
    private val authToken: String,

    @Value("\${identity.sms.twilio.from-number}")
    private val fromNumber: String
) : SmsProvider {

    private val logger = LoggerFactory.getLogger(TwilioSmsProvider::class.java)

    @PostConstruct
    fun init() {
        if (accountSid.isNotBlank() && authToken.isNotBlank()) {
            Twilio.init(accountSid, authToken)
            logger.info("Twilio SMS provider initialized with account SID: ${accountSid.take(10)}...")
        } else {
            logger.warn("Twilio credentials not configured - SMS sending will fail")
        }
    }

    override fun send(to: String, message: String): Either<SmsResult.Failure, SmsResult.Success> {
        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            logger.error("Twilio credentials not configured")
            return SmsResult.Failure(
                code = "CONFIG_ERROR",
                message = "SMS provider not configured",
                isRetryable = false
            ).left()
        }

        if (!isValidPhoneNumber(to)) {
            logger.warn("Invalid phone number format: {}", to.take(6))
            return SmsResult.Failure(
                code = "INVALID_PHONE",
                message = "Invalid phone number format",
                isRetryable = false
            ).left()
        }

        return try {
            val twilioMessage = Message.creator(
                PhoneNumber(to),
                PhoneNumber(fromNumber),
                message
            ).create()

            logger.info("SMS sent successfully to {} (SID: {})", maskPhoneNumber(to), twilioMessage.sid)

            SmsResult.Success(messageId = twilioMessage.sid).right()
        } catch (e: ApiException) {
            logger.error("Twilio API error sending SMS to {}: {} (code: {})",
                maskPhoneNumber(to), e.message, e.code)

            val isRetryable = e.code in RETRYABLE_ERROR_CODES
            SmsResult.Failure(
                code = "TWILIO_${e.code}",
                message = e.message ?: "Unknown Twilio error",
                isRetryable = isRetryable
            ).left()
        } catch (e: Exception) {
            logger.error("Unexpected error sending SMS to {}: {}", maskPhoneNumber(to), e.message, e)
            SmsResult.Failure(
                code = "SEND_ERROR",
                message = "Failed to send SMS",
                isRetryable = true
            ).left()
        }
    }

    private fun maskPhoneNumber(phone: String): String {
        // Need at least 7 chars to show prefix (3) + suffix (4) without overlap
        if (phone.length < 7) return "***-***-****"
        return "${phone.take(3)}***${phone.takeLast(4)}"
    }

    companion object {
        // Twilio error codes that indicate transient failures
        private val RETRYABLE_ERROR_CODES = setOf(
            20003, // Permission denied (rate limit)
            20429, // Too many requests
            30001, // Queue overflow
            30002, // Account suspended (may recover)
            30003, // Unreachable destination
            30004, // Message blocked
            30005, // Unknown destination
            30006, // Landline detected
            30007, // Carrier violation
            30008, // Unknown error
        )
    }
}
