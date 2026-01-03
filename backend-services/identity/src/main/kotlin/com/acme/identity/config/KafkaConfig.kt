package com.acme.identity.config

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

/**
 * Kafka producer configuration for the identity service.
 *
 * Configures a reliable Kafka producer with:
 * - Idempotent production to prevent duplicates
 * - All-broker acknowledgment for durability
 * - Retry logic for transient failures
 * - In-order delivery guarantees
 *
 * @property bootstrapServers Comma-separated list of Kafka broker addresses.
 */
@Configuration
class KafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers}")
    private val bootstrapServers: String
) {

    /**
     * Creates the Kafka producer factory with reliability settings.
     *
     * Configuration:
     * - `acks=all`: Wait for all in-sync replicas to acknowledge
     * - `retries=3`: Retry failed sends up to 3 times
     * - `max.in.flight.requests.per.connection=1`: Ensure ordering
     * - `enable.idempotence=true`: Prevent duplicate messages
     *
     * @return The configured [ProducerFactory].
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
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true
        )
        return DefaultKafkaProducerFactory(configProps)
    }

    /**
     * Creates the Kafka template for sending messages.
     *
     * @return The configured [KafkaTemplate].
     */
    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory())
    }
}
