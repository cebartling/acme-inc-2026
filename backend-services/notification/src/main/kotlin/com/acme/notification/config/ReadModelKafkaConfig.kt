package com.acme.notification.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries

/**
 * Kafka consumer setup for the delivery read model's projector (PIN-549), apart from the email
 * consumers' own factory, which waits five minutes between retries.
 *
 * - Manual acknowledgment, so a snapshot is acknowledged only after it is in MongoDB
 * - A failing message is retried at 1s, 2s, 4s, 8s and 16s; after that the error is logged and
 *   the listener moves on
 * - The listener starts with the application unless `notification.events.read-model-listener-enabled`
 *   is false. Tests that provide no Kafka turn it off, so they do not join the real consumer group.
 */
@Configuration
class ReadModelKafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers}")
    private val bootstrapServers: String,

    @Value("\${spring.kafka.consumer.group-id}")
    private val groupId: String,

    @Value("\${notification.events.read-model-listener-enabled:true}")
    private val listenerEnabled: Boolean
) {

    @Bean
    fun readModelListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
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
        factory.setCommonErrorHandler(
            DefaultErrorHandler(
                ExponentialBackOffWithMaxRetries(5).apply {
                    initialInterval = 1_000
                    multiplier = 2.0
                    maxInterval = 16_000
                }
            )
        )
        return factory
    }
}
