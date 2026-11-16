package com.acme.cart.config

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
 * Kafka consumer configuration for the cart's read model projector (PIN-551).
 *
 * Like customer's, it is declared here and not through `spring.kafka.*`: without `@EnableKafka`
 * the `@KafkaListener` is never started. The projector acknowledges each snapshot once it has
 * been applied, so a failed one is retried, and after the last retry is logged and skipped. The
 * next snapshot of the same cart replaces whatever the skipped one would have written.
 */
@Configuration
@EnableKafka
class KafkaConsumerConfig(
    @Value("\${spring.kafka.bootstrap-servers}")
    private val bootstrapServers: String,

    @Value("\${spring.kafka.consumer.group-id}")
    private val groupId: String
) {

    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> = DefaultKafkaConsumerFactory(
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

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> =
        ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory())
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            // Retry a failed snapshot 5 times, a second apart, then log it and move on
            setCommonErrorHandler(DefaultErrorHandler(FixedBackOff(1000L, 5L)))
        }
}
