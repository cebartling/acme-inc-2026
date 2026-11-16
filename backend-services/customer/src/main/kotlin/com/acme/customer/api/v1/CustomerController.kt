package com.acme.customer.api.v1

import com.acme.customer.api.v1.dto.CustomerResponse
import com.acme.customer.api.v1.dto.ProfileCompletenessResponse
import com.acme.customer.api.v1.dto.ProfileResponse
import com.acme.customer.api.v1.dto.UpdateProfileRequest
import com.acme.customer.api.v1.dto.UpdateProfileResponse
import com.acme.customer.application.UpdateProfileResult
import com.acme.customer.application.UpdateProfileUseCase
import com.acme.customer.infrastructure.projection.CustomerReadModelQueries
import com.acme.customer.infrastructure.projection.ReadAfter
import com.acme.customer.infrastructure.tracking.CustomerActivityTracker
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
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
    private val readModel: CustomerReadModelQueries,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val activityTracker: CustomerActivityTracker
) {
    private val logger = LoggerFactory.getLogger(CustomerController::class.java)

    /**
     * Gets a customer by ID from the read model.
     *
     * Send `X-Read-After` with the value a write returned to wait until that write is visible.
     *
     * @param id The customer ID (UUID).
     * @return The customer, 404 if not found, or 503 if the read model has not caught up.
     */
    @GetMapping("/{id:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}")
    fun getCustomer(
        @PathVariable id: String,
        @RequestHeader(ReadAfter.HEADER, required = false) readAfter: UUID? = null
    ): ResponseEntity<CustomerResponse> {
        logger.debug("Getting customer by ID: {}", id)

        val customerId = try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid customer ID format: {}", id)
            return ResponseEntity.badRequest().build()
        }

        val snapshot = readModel.findById(customerId, readAfter)
            ?: run {
                logger.debug("Customer not found: {}", id)
                return ResponseEntity.notFound().build()
            }

        return ResponseEntity.ok(snapshot.customer)
    }

    /**
     * Gets a customer by the identity user ID, from the read model.
     *
     * @param userId The identity user ID (UUID).
     * @return The customer, 404 if not found, or 503 if the read model has not caught up.
     */
    @GetMapping("/by-user/{userId}")
    fun getCustomerByUserId(
        @PathVariable userId: String,
        @RequestHeader(ReadAfter.HEADER, required = false) readAfter: UUID? = null
    ): ResponseEntity<CustomerResponse> {
        logger.debug("Getting customer by user ID: {}", userId)

        val parsedUserId = try {
            UUID.fromString(userId)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid user ID format: {}", userId)
            return ResponseEntity.badRequest().build()
        }

        val snapshot = readModel.findByUserId(parsedUserId, readAfter)
            ?: run {
                logger.debug("Customer not found for user: {}", userId)
                return ResponseEntity.notFound().build()
            }

        return ResponseEntity.ok(snapshot.customer)
    }

    /**
     * Gets a customer by customer number, from the read model.
     *
     * @param customerNumber The customer number (e.g. ACME-202601-000001).
     * @return The customer, 404 if not found, or 503 if the read model has not caught up.
     */
    @GetMapping("/by-number/{customerNumber}")
    fun getCustomerByNumber(
        @PathVariable customerNumber: String,
        @RequestHeader(ReadAfter.HEADER, required = false) readAfter: UUID? = null
    ): ResponseEntity<CustomerResponse> {
        logger.debug("Getting customer by number: {}", customerNumber)

        val snapshot = readModel.findByCustomerNumber(customerNumber, readAfter)
            ?: run {
                logger.debug("Customer not found with number: {}", customerNumber)
                return ResponseEntity.notFound().build()
            }

        return ResponseEntity.ok(snapshot.customer)
    }

    /**
     * Gets a customer by email address, from the read model.
     *
     * @param email The email address.
     * @return The customer, 404 if not found, or 503 if the read model has not caught up.
     */
    @GetMapping("/by-email/{email}")
    fun getCustomerByEmail(
        @PathVariable email: String,
        @RequestHeader(ReadAfter.HEADER, required = false) readAfter: UUID? = null
    ): ResponseEntity<CustomerResponse> {
        logger.debug("Getting customer by email: {}", email)

        val snapshot = readModel.findByEmail(email, readAfter)
            ?: run {
                logger.debug("Customer not found with email: {}", email)
                return ResponseEntity.notFound().build()
            }

        return ResponseEntity.ok(snapshot.customer)
    }

    /**
     * Gets the current user's customer profile, from the read model.
     *
     * The user comes from the JWT subject. A successful read also records the user's activity.
     *
     * @param jwt The authenticated user's access token.
     * @return The customer, 404 if not found, or 503 if the read model has not caught up.
     */
    @GetMapping("/me")
    fun getCurrentCustomer(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader(ReadAfter.HEADER, required = false) readAfter: UUID? = null
    ): ResponseEntity<CustomerResponse> {
        val userId = UUID.fromString(jwt.subject)
        logger.debug("Getting current customer for user: {}", userId)

        val snapshot = readModel.findByUserId(userId, readAfter)
            ?: run {
                logger.debug("Customer not found for user: {}", userId)
                return ResponseEntity.notFound().build()
            }

        activityTracker.trackAsync(userId)
        return ResponseEntity.ok(snapshot.customer)
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
        @AuthenticationPrincipal(expression = "subject") userId: String,
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

    /**
     * Gets the profile completeness score and breakdown for a customer.
     *
     * Returns detailed information about profile completion including:
     * - Overall score (0-100%)
     * - Section-by-section breakdown with weights
     * - Individual items within each section
     * - Next recommended action
     *
     * @param id The customer ID (UUID).
     * @return The profile completeness breakdown or 404 if not found.
     */
    @GetMapping("/{id}/profile/completeness")
    fun getProfileCompleteness(
        @PathVariable id: String,
        @AuthenticationPrincipal(expression = "subject") userId: String,
        @RequestHeader(ReadAfter.HEADER, required = false) readAfter: UUID? = null
    ): ResponseEntity<ProfileCompletenessResponse> {
        logger.debug("Getting profile completeness for customer: {}", id)

        val customerId = try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid customer ID format: {}", id)
            return ResponseEntity.badRequest().build()
        }

        val snapshot = readModel.findById(customerId, readAfter)
            ?: run {
                logger.debug("Customer not found: {}", id)
                return ResponseEntity.notFound().build()
            }

        if (snapshot.customer.userId != userId) {
            logger.debug("User {} not authorized to access customer {}", userId, id)
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok(snapshot.completeness)
    }
}
