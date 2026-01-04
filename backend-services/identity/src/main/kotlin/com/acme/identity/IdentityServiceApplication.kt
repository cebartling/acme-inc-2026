package com.acme.identity

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Main application class for the Identity Service.
 *
 * The Identity Service provides centralized authentication and identity
 * management for the ACME e-commerce platform, including:
 * - User registration with email verification
 * - Password management with Argon2id hashing
 * - Event publishing for downstream services
 *
 * @see <a href="../../documentation/user-stories/0002-create-customer-profile">User Story US-0002</a>
 */
@SpringBootApplication
class IdentityServiceApplication

/**
 * Application entry point.
 *
 * @param args Command-line arguments.
 */
fun main(args: Array<String>) {
    runApplication<IdentityServiceApplication>(*args)
}
