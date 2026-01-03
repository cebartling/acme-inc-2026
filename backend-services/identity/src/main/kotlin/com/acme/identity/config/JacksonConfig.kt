package com.acme.identity.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Jackson JSON serialization configuration.
 *
 * Configures the [ObjectMapper] with Kotlin support and proper
 * date/time handling for the identity service.
 */
@Configuration
class JacksonConfig {

    /**
     * Creates the primary ObjectMapper for JSON serialization.
     *
     * Configuration:
     * - Kotlin module for data class support
     * - Java Time module for java.time.* types
     * - ISO-8601 date format instead of timestamps
     *
     * @return The configured [ObjectMapper].
     */
    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
}
