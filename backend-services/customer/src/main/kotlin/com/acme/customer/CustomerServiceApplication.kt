package com.acme.customer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

/**
 * Main application class for the Customer Service.
 *
 * The Customer Service manages customer profiles for the ACME e-commerce platform,
 * including:
 * - Consuming UserRegistered events to create customer profiles
 * - Managing customer information and preferences
 * - Publishing CustomerRegistered events for downstream services
 * - Projecting read models to MongoDB for queries
 *
 * @see <a href="../../documentation/user-stories/0002-create-customer-profile">User Story US-0002</a>
 */
@SpringBootApplication
@EnableAsync
class CustomerServiceApplication

/**
 * Application entry point.
 *
 * @param args Command-line arguments.
 */
fun main(args: Array<String>) {
    runApplication<CustomerServiceApplication>(*args)
}
