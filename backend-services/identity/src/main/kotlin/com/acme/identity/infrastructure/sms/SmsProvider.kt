package com.acme.identity.infrastructure.sms

import arrow.core.Either

/**
 * Result of attempting to send an SMS.
 */
sealed interface SmsResult {
    /**
     * SMS was sent successfully.
     *
     * @property messageId Provider-specific message ID for tracking.
     */
    data class Success(val messageId: String) : SmsResult

    /**
     * SMS sending failed.
     *
     * @property code Error code.
     * @property message Human-readable error message.
     * @property isRetryable Whether the error is transient and can be retried.
     */
    data class Failure(
        val code: String,
        val message: String,
        val isRetryable: Boolean = false
    ) : SmsResult
}

/**
 * Interface for SMS sending providers.
 *
 * Implementations should handle provider-specific authentication and API calls.
 * All implementations must be thread-safe.
 */
interface SmsProvider {

    /**
     * Sends an SMS message to the specified phone number.
     *
     * @param to Phone number in E.164 format (e.g., +15551234567).
     * @param message The message body to send.
     * @return Either a failure or success result.
     */
    fun send(to: String, message: String): Either<SmsResult.Failure, SmsResult.Success>

    /**
     * Validates that a phone number is in acceptable format.
     *
     * @param phoneNumber The phone number to validate.
     * @return true if the phone number is valid.
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // E.164 format: + followed by 1-15 digits
        return phoneNumber.matches(Regex("^\\+[1-9]\\d{1,14}$"))
    }
}
