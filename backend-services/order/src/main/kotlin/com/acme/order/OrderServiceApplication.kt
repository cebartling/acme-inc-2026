package com.acme.order

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Main application class for the Order Service.
 *
 * The Order Service owns customer orders for the ACME e-commerce platform (Epic 005,
 * PIN-58). This is the skeleton (PIN-364): placing and reading orders come with
 * PIN-366 and PIN-367.
 *
 * @see <a href="../../documentation/epics/005-order-management.md">Epic 005: Order Management</a>
 */
@SpringBootApplication
class OrderServiceApplication

/**
 * Application entry point.
 *
 * @param args Command-line arguments.
 */
fun main(args: Array<String>) {
    runApplication<OrderServiceApplication>(*args)
}
