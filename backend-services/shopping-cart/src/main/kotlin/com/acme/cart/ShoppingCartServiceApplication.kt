package com.acme.cart

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Main application class for the Shopping Cart Service.
 *
 * The Shopping Cart Service owns guest and customer shopping carts for the ACME
 * e-commerce platform. A guest cart is keyed by the session ID carried in the
 * `acme_session_id` cookie.
 *
 * @see <a href="../../documentation/user-stories/0004-customer-shopping-experience">User Story US-0004</a>
 */
@SpringBootApplication
class ShoppingCartServiceApplication

/**
 * Application entry point.
 *
 * @param args Command-line arguments.
 */
fun main(args: Array<String>) {
    runApplication<ShoppingCartServiceApplication>(*args)
}
