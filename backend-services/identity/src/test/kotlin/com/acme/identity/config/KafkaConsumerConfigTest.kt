package com.acme.identity.config

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The projector's listener starts with the application unless `identity.events.read-model-listener-enabled`
 * is false (PIN-548). Tests that provide no Kafka turn it off: a listener there would join the real
 * `identity-service` group on localhost:9092 and take partitions from a running local stack.
 */
class KafkaConsumerConfigTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(KafkaConsumerConfig::class.java)
        .withPropertyValues(
            "spring.kafka.bootstrap-servers=localhost:9092",
            "spring.kafka.consumer.group-id=identity-service-test"
        )

    private fun autoStartup(context: org.springframework.context.ApplicationContext): Boolean =
        context.getBean(ConcurrentKafkaListenerContainerFactory::class.java)
            .createContainer("identity.read-model")
            .isAutoStartup

    @Test
    fun `the listener starts with the application by default`() {
        runner.run { context -> assertTrue(autoStartup(context)) }
    }

    @Test
    fun `the listener does not start when the switch is off`() {
        runner.withPropertyValues("identity.events.read-model-listener-enabled=false").run { context ->
            assertFalse(autoStartup(context))
        }
    }
}
