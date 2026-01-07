package com.acme.notification

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Main entry point for the Notification Service.
 *
 * This service is responsible for sending notifications to users,
 * including verification emails when users register. It consumes
 * events from Kafka and sends emails via SendGrid.
 */
@SpringBootApplication
class NotificationServiceApplication

fun main(args: Array<String>) {
    runApplication<NotificationServiceApplication>(*args)
}
