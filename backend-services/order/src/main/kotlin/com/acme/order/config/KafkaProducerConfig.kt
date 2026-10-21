package com.acme.order.config

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import java.util.concurrent.TimeUnit

/**
 * Kafka producer configuration for the Order Service.
 *
 * Configures the producer with:
 * - Idempotence enabled to prevent duplicate messages
 * - All replicas acknowledgment for durability
 * - Ordering guarantees via max.in.flight.requests.per.connection=1
 * - send() blocking (waiting for metadata, e.g. while Kafka is down) capped at the publish
 *   timeout, so OutboxRelay's timeout holds instead of the producer's 60s default
 */
@Configuration
class KafkaProducerConfig(
    @Value("\${spring.kafka.bootstrap-servers}")
    private val bootstrapServers: String,
    @Value("\${acme.order.events.publish-timeout-seconds:10}")
    private val publishTimeoutSeconds: Long
) {

    /**
     * Creates the Kafka producer factory with reliability settings.
     */
    @Bean
    fun producerFactory(): ProducerFactory<String, String> {
        val configProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 1,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
            ProducerConfig.MAX_BLOCK_MS_CONFIG to TimeUnit.SECONDS.toMillis(publishTimeoutSeconds)
        )
        return DefaultKafkaProducerFactory(configProps)
    }

    /**
     * Creates the KafkaTemplate for sending messages.
     */
    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory())
    }
}
