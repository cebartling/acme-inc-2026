package com.acme.customer.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOff

/**
 * Kafka consumer configuration for the Customer Service.
 *
 * Configures separate consumer groups for different event types:
 * - userRegisteredConsumerGroup: Processes UserRegistered events for customer creation
 * - userActivatedConsumerGroup: Processes UserActivated events for customer activation
 *
 * Each consumer group independently consumes from the same topic, allowing
 * both consumers to receive all messages and filter for their specific event types.
 *
 * Common configuration:
 * - Manual acknowledgment for reliable processing
 * - Exponential backoff retry (1s, 2s, 4s, 8s, 16s)
 * - Read committed isolation for transactional producers
 */
@Configuration
@EnableKafka
class KafkaConsumerConfig(
    @Value("\${spring.kafka.bootstrap-servers}")
    private val bootstrapServers: String,

    @Value("\${spring.kafka.consumer.group-id}")
    private val baseGroupId: String,

    @Value("\${customer.retry.max-attempts:5}")
    private val maxRetryAttempts: Int,

    @Value("\${customer.retry.initial-interval-ms:1000}")
    private val initialIntervalMs: Long,

    @Value("\${customer.retry.multiplier:2.0}")
    private val retryMultiplier: Double,

    @Value("\${customer.retry.max-interval-ms:16000}")
    private val maxIntervalMs: Long
) {

    /**
     * Creates a Kafka consumer factory with the specified group ID.
     */
    private fun createConsumerFactory(groupId: String): ConsumerFactory<String, String> {
        val configProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.ISOLATION_LEVEL_CONFIG to "read_committed",
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 10,
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to 30000
        )
        return DefaultKafkaConsumerFactory(configProps)
    }

    /**
     * Consumer factory for UserRegistered events.
     */
    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> {
        return createConsumerFactory("$baseGroupId-user-registered")
    }

    /**
     * Consumer factory for UserActivated events.
     */
    @Bean
    fun userActivatedConsumerFactory(): ConsumerFactory<String, String> {
        return createConsumerFactory("$baseGroupId-user-activated")
    }

    /**
     * Kafka listener container factory for UserRegistered events.
     * Uses a dedicated consumer group for processing registration events.
     */
    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.setConsumerFactory(consumerFactory())
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.setConcurrency(3)
        factory.setCommonErrorHandler(defaultErrorHandler())
        return factory
    }

    /**
     * Kafka listener container factory for UserActivated events.
     * Uses a dedicated consumer group for processing activation events.
     */
    @Bean
    fun userActivatedKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.setConsumerFactory(userActivatedConsumerFactory())
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.setConcurrency(3)
        factory.setCommonErrorHandler(defaultErrorHandler())
        return factory
    }

    /**
     * Creates the error handler with exponential backoff retry.
     *
     * Retry intervals: 1s, 2s, 4s, 8s, 16s (max 5 attempts)
     * After max retries, the error is logged and processing continues.
     */
    @Bean
    fun defaultErrorHandler(): DefaultErrorHandler {
        val backOff = ExponentialBackOff().apply {
            initialInterval = initialIntervalMs
            multiplier = retryMultiplier
            maxInterval = maxIntervalMs
            maxElapsedTime = calculateMaxElapsedTime()
        }

        return DefaultErrorHandler(
            { record, exception ->
                // Log the failure after all retries are exhausted
                org.slf4j.LoggerFactory.getLogger(KafkaConsumerConfig::class.java)
                    .error(
                        "Failed to process record from topic {} partition {} offset {} after {} retries: {}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        maxRetryAttempts,
                        exception.message,
                        exception
                    )
            },
            backOff
        )
    }

    /**
     * Calculates the maximum elapsed time for retries.
     * For 5 retries with 1s, 2s, 4s, 8s, 16s = 31s total + buffer
     */
    private fun calculateMaxElapsedTime(): Long {
        var total = 0L
        var interval = initialIntervalMs
        repeat(maxRetryAttempts) {
            total += interval
            interval = (interval * retryMultiplier).toLong().coerceAtMost(maxIntervalMs)
        }
        return total + 5000 // Add 5 second buffer
    }
}
