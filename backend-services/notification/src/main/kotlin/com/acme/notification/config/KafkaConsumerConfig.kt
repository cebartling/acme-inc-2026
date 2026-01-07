package com.acme.notification.config

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
import org.springframework.util.backoff.FixedBackOff

/**
 * Kafka consumer configuration for the Notification Service.
 *
 * Configures the consumer with:
 * - Manual acknowledgment for reliable processing
 * - Fixed backoff retry (5 minutes between retries, up to 3 attempts)
 * - Read committed isolation for transactional producers
 */
@Configuration
@EnableKafka
class KafkaConsumerConfig(
    @Value("\${spring.kafka.bootstrap-servers}")
    private val bootstrapServers: String,

    @Value("\${spring.kafka.consumer.group-id}")
    private val groupId: String,

    @Value("\${notification.retry.max-attempts:3}")
    private val maxRetryAttempts: Int,

    @Value("\${notification.retry.initial-interval-ms:300000}")
    private val retryIntervalMs: Long
) {

    /**
     * Creates the Kafka consumer factory with appropriate configuration.
     */
    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> {
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
     * Creates the Kafka listener container factory with manual acknowledgment
     * and error handling with fixed backoff (5 minute intervals).
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
     * Creates the error handler with fixed backoff retry.
     *
     * Per the user story requirements:
     * - Retries up to 3 times
     * - 5 minute intervals between retries
     */
    @Bean
    fun defaultErrorHandler(): DefaultErrorHandler {
        // Configure retry backoff
        // Note: maxRetryAttempts includes the initial attempt, so we subtract 1 for FixedBackOff
        // which expects the number of retry attempts (excluding the initial attempt).
        // With maxRetryAttempts = 3: 1 initial attempt + 2 retries = 3 total attempts
        val backOff = FixedBackOff(retryIntervalMs, (maxRetryAttempts - 1).toLong())

        return DefaultErrorHandler(
            { record, exception ->
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
}
