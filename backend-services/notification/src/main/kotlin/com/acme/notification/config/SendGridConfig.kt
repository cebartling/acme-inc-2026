package com.acme.notification.config

import com.sendgrid.SendGrid
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for SendGrid email client.
 */
@Configuration
class SendGridConfig(
    @Value("\${sendgrid.api-key}")
    private val apiKey: String
) {

    /**
     * Creates the SendGrid client bean.
     */
    @Bean
    fun sendGrid(): SendGrid {
        return SendGrid(apiKey)
    }
}
