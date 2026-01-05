package com.acme.customer.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Jackson configuration for JSON serialization/deserialization.
 *
 * Configures ObjectMapper with:
 * - Kotlin module for data class support
 * - JavaTimeModule for java.time.* types
 * - ISO-8601 date formatting (not timestamps)
 */
@Configuration
class JacksonConfig {

    /**
     * Creates the primary ObjectMapper with Kotlin and Java Time support.
     */
    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerModule(kotlinModule())
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
}
