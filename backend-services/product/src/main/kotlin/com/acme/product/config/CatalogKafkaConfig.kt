package com.acme.product.config

import com.fasterxml.jackson.core.JsonProcessingException
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries

/**
 * Kafka consumer setup for the catalog read model's projector (PIN-550). Product otherwise only
 * produces (the analytics events), so this is its only consumer.
 *
 * - Manual acknowledgment, so a row change is acknowledged only after it is in MongoDB
 * - A failing message is retried at 1s, 2s, 4s, 8s and 16s; after that the error is logged and
 *   the listener moves on. A message that is invalid (bad JSON, no id, an unknown table) is not
 *   retried, but logged and skipped at once.
 * - The listener starts with the application unless `product.catalog.listener-enabled` is false.
 *   Tests that provide no Kafka turn it off, so they do not join the real consumer group.
 */
@Configuration
@EnableKafka
class CatalogKafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers}")
    private val bootstrapServers: String,

    @Value("\${spring.kafka.consumer.group-id}")
    private val groupId: String,

    @Value("\${product.catalog.listener-enabled:true}")
    private val listenerEnabled: Boolean
) {

    @Bean
    fun catalogListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val consumerFactory = DefaultKafkaConsumerFactory<String, String>(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to groupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.ISOLATION_LEVEL_CONFIG to "read_committed"
            )
        )
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.setConsumerFactory(consumerFactory)
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.setAutoStartup(listenerEnabled)
        factory.setCommonErrorHandler(catalogErrorHandler())
        return factory
    }

    fun catalogErrorHandler() = DefaultErrorHandler(
        ExponentialBackOffWithMaxRetries(5).apply {
            initialInterval = 1_000
            multiplier = 2.0
            maxInterval = 16_000
        }
    ).apply {
        // Bad input never gets better, and one consumer thread serves all four topics, so retrying it
        // would hold up every row change behind it for about 31 seconds. Only an infrastructure
        // error such as MongoDB being down is worth the backoff.
        addNotRetryableExceptions(IllegalArgumentException::class.java, JsonProcessingException::class.java)
    }
}
