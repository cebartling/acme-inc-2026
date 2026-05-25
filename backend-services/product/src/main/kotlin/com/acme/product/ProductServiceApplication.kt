package com.acme.product

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Main application class for the Product Service.
 *
 * The Product Service manages the product catalog for the ACME e-commerce platform,
 * including:
 * - Full-text product search via PostgreSQL tsvector/tsquery
 * - Publishing SearchExecuted analytics events to Kafka
 *
 * @see <a href="../../documentation/user-stories/0004-customer-shopping-experience">User Story US-0004</a>
 */
@SpringBootApplication
class ProductServiceApplication

/**
 * Application entry point.
 *
 * @param args Command-line arguments.
 */
fun main(args: Array<String>) {
    runApplication<ProductServiceApplication>(*args)
}
