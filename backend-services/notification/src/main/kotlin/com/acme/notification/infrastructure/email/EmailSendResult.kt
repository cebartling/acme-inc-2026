package com.acme.notification.infrastructure.email

/**
 * Sealed class representing the result of an email send operation.
 */
sealed class EmailSendResult {
    /**
     * Email was sent successfully.
     *
     * @property messageId The message ID from the email provider.
     * @property statusCode The HTTP status code from the provider.
     */
    data class Success(
        val messageId: String?,
        val statusCode: Int
    ) : EmailSendResult()

    /**
     * Email sending failed.
     *
     * @property message Error message.
     * @property statusCode The HTTP status code from the provider, if available.
     * @property cause The underlying exception, if any.
     */
    data class Failure(
        val message: String,
        val statusCode: Int? = null,
        val cause: Throwable? = null
    ) : EmailSendResult()
}
