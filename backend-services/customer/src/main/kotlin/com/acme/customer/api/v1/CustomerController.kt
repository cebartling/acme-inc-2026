package com.acme.customer.api.v1

import com.acme.customer.api.v1.dto.CustomerResponse
import com.acme.customer.api.v1.dto.ProfileResponse
import com.acme.customer.api.v1.dto.UpdateProfileRequest
import com.acme.customer.api.v1.dto.UpdateProfileResponse
import com.acme.customer.application.UpdateProfileResult
import com.acme.customer.application.UpdateProfileUseCase
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for customer profile operations.
 *
 * Provides endpoints for querying and updating customer profiles.
 * Note: Customer creation is handled via Kafka events, not REST API.
 */
@RestController
@RequestMapping("/api/v1/customers")
class CustomerController(
    private val customerRepository: CustomerRepository,
    private val customerPreferencesRepository: CustomerPreferencesRepository,
    private val updateProfileUseCase: UpdateProfileUseCase
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

    /**
     * Gets a customer profile by email address.
     *
     * @param email The email address to search for.
     * @return The customer profile or 404 if not found.
     */
    @GetMapping("/by-email/{email}")
    fun getCustomerByEmail(@PathVariable email: String): ResponseEntity<CustomerResponse> {
        logger.debug("Getting customer by email: {}", email)

        val customer = customerRepository.findByEmail(email)
            ?: run {
                logger.debug("Customer not found with email: {}", email)
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
     * Updates a customer's profile (partial update).
     *
     * This endpoint supports partial updates following PATCH semantics.
     * Only fields present in the request body will be updated.
     *
     * @param id The customer ID (UUID).
     * @param userId The user ID from the Authorization header (for authorization).
     * @param correlationId Optional correlation ID for distributed tracing.
     * @param request The profile update request.
     * @return The updated profile or appropriate error response.
     */
    @PatchMapping("/{id}/profile")
    fun updateProfile(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
        @RequestBody request: UpdateProfileRequest
    ): ResponseEntity<Any> {
        logger.debug("Updating profile for customer: {}", id)

        val customerId = try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid customer ID format: {}", id)
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Invalid customer ID format"))
        }

        val parsedUserId = try {
            UUID.fromString(userId)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid user ID format: {}", userId)
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Invalid user ID format"))
        }

        val parsedCorrelationId = correlationId?.let {
            try {
                UUID.fromString(it)
            } catch (e: IllegalArgumentException) {
                UUID.randomUUID()
            }
        } ?: UUID.randomUUID()

        return when (val result = updateProfileUseCase.execute(customerId, parsedUserId, request, parsedCorrelationId)) {
            is UpdateProfileResult.Success -> {
                val customer = result.customer
                ResponseEntity.ok(
                    UpdateProfileResponse(
                        customerId = customer.id.toString(),
                        profile = ProfileResponse(
                            dateOfBirth = customer.dateOfBirth,
                            gender = customer.gender,
                            preferredLocale = customer.preferredLocale,
                            timezone = customer.timezone,
                            preferredCurrency = customer.preferredCurrency
                        ),
                        profileCompleteness = customer.profileCompleteness,
                        updatedAt = customer.updatedAt.toString()
                    )
                )
            }
            is UpdateProfileResult.NotFound -> {
                ResponseEntity.notFound().build()
            }
            is UpdateProfileResult.Unauthorized -> {
                ResponseEntity.status(403)
                    .body(mapOf("error" to "You are not authorized to update this profile"))
            }
            is UpdateProfileResult.ValidationFailed -> {
                ResponseEntity.badRequest()
                    .body(mapOf("errors" to result.errors))
            }
            is UpdateProfileResult.NoUpdates -> {
                ResponseEntity.badRequest()
                    .body(mapOf("error" to "No updates provided"))
            }
            is UpdateProfileResult.Failure -> {
                logger.error("Failed to update profile: {}", result.message, result.cause)
                ResponseEntity.internalServerError()
                    .body(mapOf("error" to "An internal error occurred"))
            }
        }
    }
}
