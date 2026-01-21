package com.acme.identity.infrastructure.sms

import arrow.core.Either
import arrow.core.right
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock implementation of [SmsProvider] for testing and development.
 *
 * Logs SMS messages instead of actually sending them.
 * Stores sent messages for verification in tests.
 */
@Component
@ConditionalOnProperty(name = ["identity.sms.provider"], havingValue = "mock")
class MockSmsProvider : SmsProvider {

    private val logger = LoggerFactory.getLogger(MockSmsProvider::class.java)

    /**
     * In-memory storage of sent messages for test verification.
     * Key is the phone number, value is a list of messages sent to that number.
     */
    private val sentMessages = ConcurrentHashMap<String, MutableList<SentMessage>>()

    data class SentMessage(
        val messageId: String,
        val to: String,
        val body: String,
        val sentAt: Long = System.currentTimeMillis()
    )

    override fun send(to: String, message: String): Either<SmsResult.Failure, SmsResult.Success> {
        val messageId = "mock_${UUID.randomUUID()}"

        logger.info("=== MOCK SMS ===")
        logger.info("To: $to")
        logger.info("Body: $message")
        logger.info("Message ID: $messageId")
        logger.info("================")

        // Store for test verification
        sentMessages.computeIfAbsent(to) { mutableListOf() }
            .add(SentMessage(messageId, to, message))

        return SmsResult.Success(messageId = messageId).right()
    }

    /**
     * Gets all messages sent to a phone number.
     * For test verification.
     */
    fun getMessagesSentTo(phoneNumber: String): List<SentMessage> {
        return sentMessages[phoneNumber]?.toList() ?: emptyList()
    }

    /**
     * Gets the most recent message sent to a phone number.
     * For test verification.
     */
    fun getLastMessageSentTo(phoneNumber: String): SentMessage? {
        return sentMessages[phoneNumber]?.lastOrNull()
    }

    /**
     * Extracts the verification code from the most recent SMS sent to a phone number.
     * Assumes the code is a 6-digit number in the message.
     */
    fun extractCodeFromLastMessage(phoneNumber: String): String? {
        val message = getLastMessageSentTo(phoneNumber) ?: return null
        val codeRegex = Regex("\\b(\\d{6})\\b")
        return codeRegex.find(message.body)?.groupValues?.get(1)
    }

    /**
     * Clears all stored messages.
     * For test cleanup.
     */
    fun clearAll() {
        sentMessages.clear()
    }

    /**
     * Clears messages sent to a specific phone number.
     * For test cleanup.
     */
    fun clearMessagesTo(phoneNumber: String) {
        sentMessages.remove(phoneNumber)
    }
}
