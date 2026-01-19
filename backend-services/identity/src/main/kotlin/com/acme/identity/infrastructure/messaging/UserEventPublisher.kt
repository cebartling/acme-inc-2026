package com.acme.identity.infrastructure.messaging

import com.acme.identity.domain.events.AccountLocked
import com.acme.identity.domain.events.AccountUnlocked
import com.acme.identity.domain.events.AuthenticationFailed
import com.acme.identity.domain.events.AuthenticationSucceeded
import com.acme.identity.domain.events.EmailVerified
import com.acme.identity.domain.events.UserActivated
import com.acme.identity.domain.events.UserRegistered
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * Kafka publisher for user-related domain events.
 *
 * Publishes events to the `identity.user.events` topic for consumption
 * by downstream services such as notifications and customer management.
 *
 * Events are serialized as JSON. The aggregate ID is used as the message
 * key to ensure all events for a user are processed in order within
 * the same partition.
 *
 * @property kafkaTemplate Spring Kafka template for message publishing.
 * @property objectMapper Jackson mapper for JSON serialization.
 */
@Component
class UserEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(UserEventPublisher::class.java)

    /**
     * Publishes a [UserRegistered] event to Kafka.
     *
     * The publish operation is asynchronous. The returned future completes
     * when Kafka acknowledges receipt of the message. Failures are logged
     * but not rethrown to avoid blocking the registration response.
     *
     * Note: This design accepts eventual consistency. The event is already
     * persisted to the event store before this method is called, so the
     * registration is durable even if Kafka publishing fails. Failed events
     * should be handled by monitoring alerts on the error logs.
     *
     * @param event The user registration event to publish.
     * @return A [CompletableFuture] that completes when publishing succeeds.
     */
    fun publish(event: UserRegistered): CompletableFuture<Void> {
        val key = event.aggregateId.toString()
        val value = objectMapper.writeValueAsString(event)

        logger.debug("Publishing UserRegistered event for user: {}", event.payload.userId)

        return kafkaTemplate.send(UserRegistered.TOPIC, key, value)
            .thenAccept { result ->
                logger.info(
                    "Published UserRegistered event for user {} to topic {} partition {} offset {}",
                    event.payload.userId,
                    result.recordMetadata.topic(),
                    result.recordMetadata.partition(),
                    result.recordMetadata.offset()
                )
            }
            .exceptionally { ex ->
                logger.error(
                    "Failed to publish UserRegistered event for user {}. " +
                    "Event is persisted in event store but not published to Kafka. " +
                    "Manual intervention may be required.",
                    event.payload.userId,
                    ex
                )
                null
            }
    }

    /**
     * Publishes an [EmailVerified] event to Kafka.
     *
     * The publish operation is asynchronous. The returned future completes
     * when Kafka acknowledges receipt of the message. Failures are logged
     * but not rethrown to avoid blocking the verification response.
     *
     * @param event The email verified event to publish.
     * @return A [CompletableFuture] that completes when publishing succeeds.
     */
    fun publishEmailVerified(event: EmailVerified): CompletableFuture<Void> {
        val key = event.aggregateId.toString()
        val value = objectMapper.writeValueAsString(event)

        logger.debug("Publishing EmailVerified event for user: {}", event.payload.userId)

        return kafkaTemplate.send(EmailVerified.TOPIC, key, value)
            .thenAccept { result ->
                logger.info(
                    "Published EmailVerified event for user {} to topic {} partition {} offset {}",
                    event.payload.userId,
                    result.recordMetadata.topic(),
                    result.recordMetadata.partition(),
                    result.recordMetadata.offset()
                )
            }
            .exceptionally { ex ->
                logger.error(
                    "Failed to publish EmailVerified event for user {}. " +
                    "Event is persisted in event store but not published to Kafka. " +
                    "Manual intervention may be required.",
                    event.payload.userId,
                    ex
                )
                null
            }
    }

    /**
     * Publishes a [UserActivated] event to Kafka.
     *
     * The publish operation is asynchronous. The returned future completes
     * when Kafka acknowledges receipt of the message. Failures are logged
     * but not rethrown to avoid blocking the activation response.
     *
     * @param event The user activated event to publish.
     * @return A [CompletableFuture] that completes when publishing succeeds.
     */
    fun publishUserActivated(event: UserActivated): CompletableFuture<Void> {
        val key = event.aggregateId.toString()
        val value = objectMapper.writeValueAsString(event)

        logger.debug("Publishing UserActivated event for user: {}", event.payload.userId)

        return kafkaTemplate.send(UserActivated.TOPIC, key, value)
            .thenAccept { result ->
                logger.info(
                    "Published UserActivated event for user {} to topic {} partition {} offset {}",
                    event.payload.userId,
                    result.recordMetadata.topic(),
                    result.recordMetadata.partition(),
                    result.recordMetadata.offset()
                )
            }
            .exceptionally { ex ->
                logger.error(
                    "Failed to publish UserActivated event for user {}. " +
                    "Event is persisted in event store but not published to Kafka. " +
                    "Manual intervention may be required.",
                    event.payload.userId,
                    ex
                )
                null
            }
    }

    /**
     * Publishes an [AuthenticationFailed] event to Kafka.
     *
     * The publish operation is asynchronous. The returned future completes
     * when Kafka acknowledges receipt of the message. Failures are logged
     * but not rethrown to avoid blocking the authentication response.
     *
     * @param event The authentication failed event to publish.
     * @return A [CompletableFuture] that completes when publishing succeeds.
     */
    fun publishAuthenticationFailed(event: AuthenticationFailed): CompletableFuture<Void> {
        val key = event.aggregateId.toString()
        val value = objectMapper.writeValueAsString(event)

        logger.debug("Publishing AuthenticationFailed event for email: {}", event.payload.email)

        return kafkaTemplate.send(AuthenticationFailed.TOPIC, key, value)
            .thenAccept { result ->
                logger.info(
                    "Published AuthenticationFailed event for email {} to topic {} partition {} offset {}",
                    event.payload.email,
                    result.recordMetadata.topic(),
                    result.recordMetadata.partition(),
                    result.recordMetadata.offset()
                )
            }
            .exceptionally { ex ->
                logger.error(
                    "Failed to publish AuthenticationFailed event for email {}. " +
                    "Event is persisted in event store but not published to Kafka. " +
                    "Manual intervention may be required.",
                    event.payload.email,
                    ex
                )
                null
            }
    }

    /**
     * Publishes an [AuthenticationSucceeded] event to Kafka.
     *
     * The publish operation is asynchronous. The returned future completes
     * when Kafka acknowledges receipt of the message. Failures are logged
     * but not rethrown to avoid blocking the authentication response.
     *
     * @param event The authentication succeeded event to publish.
     * @return A [CompletableFuture] that completes when publishing succeeds.
     */
    fun publishAuthenticationSucceeded(event: AuthenticationSucceeded): CompletableFuture<Void> {
        val key = event.aggregateId.toString()
        val value = objectMapper.writeValueAsString(event)

        logger.debug("Publishing AuthenticationSucceeded event for user: {}", event.payload.userId)

        return kafkaTemplate.send(AuthenticationSucceeded.TOPIC, key, value)
            .thenAccept { result ->
                logger.info(
                    "Published AuthenticationSucceeded event for user {} to topic {} partition {} offset {}",
                    event.payload.userId,
                    result.recordMetadata.topic(),
                    result.recordMetadata.partition(),
                    result.recordMetadata.offset()
                )
            }
            .exceptionally { ex ->
                logger.error(
                    "Failed to publish AuthenticationSucceeded event for user {}. " +
                    "Event is persisted in event store but not published to Kafka. " +
                    "Manual intervention may be required.",
                    event.payload.userId,
                    ex
                )
                null
            }
    }

    /**
     * Publishes an [AccountLocked] event to Kafka.
     *
     * The publish operation is asynchronous. The returned future completes
     * when Kafka acknowledges receipt of the message. Failures are logged
     * but not rethrown to avoid blocking the authentication response.
     *
     * This event triggers the Notification Service to send a lockout email
     * to the customer.
     *
     * @param event The account locked event to publish.
     * @return A [CompletableFuture] that completes when publishing succeeds.
     */
    fun publishAccountLocked(event: AccountLocked): CompletableFuture<Void> {
        val key = event.aggregateId.toString()
        val value = objectMapper.writeValueAsString(event)

        logger.debug("Publishing AccountLocked event for user: {}", event.payload.userId)

        return kafkaTemplate.send(AccountLocked.TOPIC, key, value)
            .thenAccept { result ->
                logger.info(
                    "Published AccountLocked event for user {} to topic {} partition {} offset {}",
                    event.payload.userId,
                    result.recordMetadata.topic(),
                    result.recordMetadata.partition(),
                    result.recordMetadata.offset()
                )
            }
            .exceptionally { ex ->
                logger.error(
                    "Failed to publish AccountLocked event for user {}. " +
                    "Event is persisted in event store but not published to Kafka. " +
                    "Manual intervention may be required.",
                    event.payload.userId,
                    ex
                )
                null
            }
    }

    /**
     * Publishes an [AccountUnlocked] event to Kafka.
     *
     * The publish operation is asynchronous. The returned future completes
     * when Kafka acknowledges receipt of the message. Failures are logged
     * but not rethrown to avoid blocking the authentication response.
     *
     * @param event The account unlocked event to publish.
     * @return A [CompletableFuture] that completes when publishing succeeds.
     */
    fun publishAccountUnlocked(event: AccountUnlocked): CompletableFuture<Void> {
        val key = event.aggregateId.toString()
        val value = objectMapper.writeValueAsString(event)

        logger.debug("Publishing AccountUnlocked event for user: {}", event.payload.userId)

        return kafkaTemplate.send(AccountUnlocked.TOPIC, key, value)
            .thenAccept { result ->
                logger.info(
                    "Published AccountUnlocked event for user {} to topic {} partition {} offset {}",
                    event.payload.userId,
                    result.recordMetadata.topic(),
                    result.recordMetadata.partition(),
                    result.recordMetadata.offset()
                )
            }
            .exceptionally { ex ->
                logger.error(
                    "Failed to publish AccountUnlocked event for user {}. " +
                    "Event is persisted in event store but not published to Kafka. " +
                    "Manual intervention may be required.",
                    event.payload.userId,
                    ex
                )
                null
            }
    }
}
