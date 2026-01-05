package com.acme.customer.api.v1

import com.acme.customer.api.v1.dto.CustomerResponse
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for customer profile operations.
 *
 * Provides endpoints for querying customer profiles.
 * Note: Customer creation is handled via Kafka events, not REST API.
 */
@RestController
@RequestMapping("/api/v1/customers")
class CustomerController(
    private val customerRepository: CustomerRepository,
    private val customerPreferencesRepository: CustomerPreferencesRepository
) {
    private val logger = LoggerFactory.getLogger(CustomerController::class.java)

    /**
     * Gets a customer profile by customer ID.
     *
     * @param id The customer ID (UUID).
     * @return The customer profile or 404 if not found.
     */
    @GetMapping("/{id}")
    fun getCustomer(@PathVariable id: String): ResponseEntity<CustomerResponse> {
        logger.debug("Getting customer by ID: {}", id)

        val customerId = try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid customer ID format: {}", id)
            return ResponseEntity.badRequest().build()
        }

        val customer = customerRepository.findById(customerId).orElse(null)
            ?: run {
                logger.debug("Customer not found: {}", id)
                return ResponseEntity.notFound().build()
            }

        val preferences = customerPreferencesRepository.findById(customerId).orElse(null)
            ?: run {
                logger.error("Preferences not found for customer: {}", id)
                return ResponseEntity.internalServerError().build()
            }

        return ResponseEntity.ok(CustomerResponse.fromDomain(customer, preferences))
    }

    /**
     * Gets a customer profile by user ID.
     *
     * @param userId The user ID from Identity Service (UUID).
     * @return The customer profile or 404 if not found.
     */
    @GetMapping("/by-user/{userId}")
    fun getCustomerByUserId(@PathVariable userId: String): ResponseEntity<CustomerResponse> {
        logger.debug("Getting customer by user ID: {}", userId)

        val parsedUserId = try {
            UUID.fromString(userId)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid user ID format: {}", userId)
            return ResponseEntity.badRequest().build()
        }

        val customer = customerRepository.findByUserId(parsedUserId)
            ?: run {
                logger.debug("Customer not found for user: {}", userId)
                return ResponseEntity.notFound().build()
            }

        val preferences = customerPreferencesRepository.findById(customer.id).orElse(null)
            ?: run {
                logger.error("Preferences not found for customer: {}", customer.id)
                return ResponseEntity.internalServerError().build()
            }

        return ResponseEntity.ok(CustomerResponse.fromDomain(customer, preferences))
    }

    /**
     * Gets a customer profile by customer number.
     *
     * @param customerNumber The human-readable customer number (ACME-YYYYMM-NNNNNN).
     * @return The customer profile or 404 if not found.
     */
    @GetMapping("/by-number/{customerNumber}")
    fun getCustomerByNumber(@PathVariable customerNumber: String): ResponseEntity<CustomerResponse> {
        logger.debug("Getting customer by number: {}", customerNumber)

        val customer = customerRepository.findByCustomerNumber(customerNumber)
            ?: run {
                logger.debug("Customer not found with number: {}", customerNumber)
                return ResponseEntity.notFound().build()
            }

        val preferences = customerPreferencesRepository.findById(customer.id).orElse(null)
            ?: run {
                logger.error("Preferences not found for customer: {}", customer.id)
                return ResponseEntity.internalServerError().build()
            }

        return ResponseEntity.ok(CustomerResponse.fromDomain(customer, preferences))
    }
}
