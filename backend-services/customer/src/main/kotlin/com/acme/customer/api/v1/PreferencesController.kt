package com.acme.customer.api.v1

import com.acme.customer.api.v1.dto.CustomerPreferencesResponse
import com.acme.customer.api.v1.dto.UpdatePreferencesRequest
import com.acme.customer.application.PreferenceUpdateContext
import com.acme.customer.application.UpdatePreferencesResult
import com.acme.customer.application.UpdatePreferencesUseCase
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for customer preference operations.
 *
 * Provides endpoints for getting and updating customer preferences.
 */
@RestController
@RequestMapping("/api/v1/customers")
class PreferencesController(
    private val customerRepository: CustomerRepository,
    private val preferencesRepository: CustomerPreferencesRepository,
    private val updatePreferencesUseCase: UpdatePreferencesUseCase
) {
    private val logger = LoggerFactory.getLogger(PreferencesController::class.java)

    /**
     * Gets a customer's preferences.
     *
     * @param id The customer ID (UUID).
     * @param userId The user ID from the Authorization header (for authorization).
     * @return The customer's preferences or appropriate error response.
     */
    @GetMapping("/{id}/preferences")
    fun getPreferences(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String
    ): ResponseEntity<Any> {
        logger.debug("Getting preferences for customer: {}", id)

        val customerId = parseUUID(id) ?: run {
            logger.warn("Invalid customer ID format: {}", id)
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Invalid customer ID format"))
        }

        val parsedUserId = parseUUID(userId) ?: run {
            logger.warn("Invalid user ID format: {}", userId)
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Invalid user ID format"))
        }

        val customer = customerRepository.findById(customerId).orElse(null)
            ?: run {
                logger.debug("Customer not found: {}", id)
                return ResponseEntity.notFound().build()
            }

        // Authorization check
        if (customer.userId != parsedUserId) {
            logger.warn("User {} not authorized to view preferences for customer {}", userId, id)
            return ResponseEntity.status(403)
                .body(mapOf("error" to "You are not authorized to view these preferences"))
        }

        val preferences = preferencesRepository.findById(customerId).orElse(null)
            ?: run {
                logger.error("Preferences not found for customer: {}", id)
                return ResponseEntity.internalServerError()
                    .body(mapOf("error" to "Preferences not found"))
            }

        return ResponseEntity.ok(CustomerPreferencesResponse.fromDomain(preferences))
    }

    /**
     * Updates a customer's preferences.
     *
     * Supports partial updates - only fields present in the request body will be updated.
     *
     * @param id The customer ID (UUID).
     * @param userId The user ID from the Authorization header (for authorization).
     * @param correlationId Optional correlation ID for distributed tracing.
     * @param clientIp Optional client IP address for GDPR compliance logging.
     * @param userAgent Optional user agent for GDPR compliance logging.
     * @param request The preferences update request.
     * @return The updated preferences or appropriate error response.
     */
    @PutMapping("/{id}/preferences")
    fun updatePreferences(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
        @RequestHeader("X-Forwarded-For", required = false) clientIp: String?,
        @RequestHeader("User-Agent", required = false) userAgent: String?,
        @RequestBody request: UpdatePreferencesRequest
    ): ResponseEntity<Any> {
        logger.debug("Updating preferences for customer: {}", id)

        val customerId = parseUUID(id) ?: run {
            logger.warn("Invalid customer ID format: {}", id)
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Invalid customer ID format"))
        }

        val parsedUserId = parseUUID(userId) ?: run {
            logger.warn("Invalid user ID format: {}", userId)
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Invalid user ID format"))
        }

        val parsedCorrelationId = correlationId?.let { parseUUID(it) } ?: UUID.randomUUID()

        val context = PreferenceUpdateContext(
            ipAddress = extractClientIp(clientIp),
            userAgent = userAgent
        )

        return when (val result = updatePreferencesUseCase.execute(
            customerId = customerId,
            userId = parsedUserId,
            request = request,
            correlationId = parsedCorrelationId,
            context = context
        )) {
            is UpdatePreferencesResult.Success -> {
                ResponseEntity.ok(CustomerPreferencesResponse.fromDomain(result.preferences))
            }
            is UpdatePreferencesResult.NotFound -> {
                ResponseEntity.notFound().build()
            }
            is UpdatePreferencesResult.Unauthorized -> {
                ResponseEntity.status(403)
                    .body(mapOf("error" to "You are not authorized to update these preferences"))
            }
            is UpdatePreferencesResult.NoUpdates -> {
                ResponseEntity.badRequest()
                    .body(mapOf("error" to "No updates provided"))
            }
            is UpdatePreferencesResult.ValidationFailed -> {
                ResponseEntity.badRequest()
                    .body(mapOf("errors" to result.errors))
            }
            is UpdatePreferencesResult.PhoneNotVerified -> {
                ResponseEntity.badRequest()
                    .body(mapOf(
                        "error" to "PHONE_NOT_VERIFIED",
                        "message" to "Phone number must be verified to enable SMS notifications"
                    ))
            }
            is UpdatePreferencesResult.UnsupportedLanguage -> {
                ResponseEntity.badRequest()
                    .body(mapOf(
                        "error" to "UNSUPPORTED_LANGUAGE",
                        "message" to "Unsupported language: ${result.locale}"
                    ))
            }
            is UpdatePreferencesResult.Failure -> {
                logger.error("Failed to update preferences: {}", result.message, result.cause)
                ResponseEntity.internalServerError()
                    .body(mapOf("error" to "An internal error occurred"))
            }
        }
    }

    /**
     * Parses a string to UUID safely.
     */
    private fun parseUUID(value: String): UUID? {
        return try {
            UUID.fromString(value)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Extracts the client IP from X-Forwarded-For header.
     * Returns the first IP if multiple are present.
     */
    private fun extractClientIp(forwardedFor: String?): String? {
        return forwardedFor?.split(",")?.firstOrNull()?.trim()
    }
}
