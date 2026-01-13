package com.acme.customer.api.v1

import com.acme.customer.api.v1.dto.ConsentHistoryExportResponse
import com.acme.customer.api.v1.dto.ConsentResponse
import com.acme.customer.api.v1.dto.ConsentStatusResponse
import com.acme.customer.api.v1.dto.ConsentsListResponse
import com.acme.customer.api.v1.dto.GrantConsentRequest
import com.acme.customer.application.ConsentUpdateContext
import com.acme.customer.application.ExportConsentHistoryResult
import com.acme.customer.application.ExportConsentHistoryUseCase
import com.acme.customer.application.GetConsentsResult
import com.acme.customer.application.GetConsentsUseCase
import com.acme.customer.application.GrantConsentResult
import com.acme.customer.application.GrantConsentUseCase
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for customer consent operations.
 *
 * Provides endpoints for granting/revoking consent, listing current consents,
 * and exporting consent history for GDPR data subject requests.
 */
@RestController
@RequestMapping("/api/v1/customers")
class ConsentController(
    private val grantConsentUseCase: GrantConsentUseCase,
    private val getConsentsUseCase: GetConsentsUseCase,
    private val exportConsentHistoryUseCase: ExportConsentHistoryUseCase
) {
    private val logger = LoggerFactory.getLogger(ConsentController::class.java)

    /**
     * Grants or revokes consent for a customer.
     *
     * @param customerId The customer ID (UUID).
     * @param userId The user ID from the Authorization header (for authorization).
     * @param correlationId Optional correlation ID for distributed tracing.
     * @param request The consent grant/revoke request.
     * @return The created consent record or appropriate error response.
     */
    @PostMapping("/{customerId}/consents")
    fun grantOrRevokeConsent(
        @PathVariable customerId: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
        @RequestBody request: GrantConsentRequest
    ): ResponseEntity<Any> {
        logger.debug(
            "Processing consent {} for customer {}: type={}, granted={}",
            if (request.granted) "grant" else "revocation",
            customerId,
            request.consentType,
            request.granted
        )

        val parsedCustomerId = parseUUID(customerId) ?: run {
            logger.warn("Invalid customer ID format: {}", customerId)
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Invalid customer ID format"))
        }

        val parsedUserId = parseUUID(userId) ?: run {
            logger.warn("Invalid user ID format: {}", userId)
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Invalid user ID format"))
        }

        val parsedCorrelationId = correlationId?.let { parseUUID(it) } ?: UUID.randomUUID()

        val context = ConsentUpdateContext(
            ipAddress = request.ipAddress,
            userAgent = request.userAgent
        )

        return when (val result = grantConsentUseCase.execute(
            customerId = parsedCustomerId,
            userId = parsedUserId,
            consentTypeString = request.consentType,
            granted = request.granted,
            sourceString = request.source,
            correlationId = parsedCorrelationId,
            context = context
        )) {
            is GrantConsentResult.Success -> {
                ResponseEntity.ok(ConsentResponse.fromDomain(result.consentRecord))
            }
            is GrantConsentResult.CustomerNotFound -> {
                ResponseEntity.notFound().build()
            }
            is GrantConsentResult.Unauthorized -> {
                ResponseEntity.status(403)
                    .body(mapOf("error" to "You are not authorized to update this customer's consents"))
            }
            is GrantConsentResult.RequiredConsentCannotBeRevoked -> {
                ResponseEntity.badRequest()
                    .body(mapOf(
                        "error" to "REQUIRED_CONSENT",
                        "message" to result.message
                    ))
            }
            is GrantConsentResult.InvalidConsentType -> {
                ResponseEntity.badRequest()
                    .body(mapOf(
                        "error" to "INVALID_CONSENT_TYPE",
                        "message" to "Invalid consent type: ${result.consentType}. " +
                                "Valid types: DATA_PROCESSING, MARKETING, ANALYTICS, THIRD_PARTY, PERSONALIZATION"
                    ))
            }
            is GrantConsentResult.InvalidConsentSource -> {
                ResponseEntity.badRequest()
                    .body(mapOf(
                        "error" to "INVALID_SOURCE",
                        "message" to "Invalid consent source: ${result.source}. " +
                                "Valid sources: REGISTRATION, PROFILE_WIZARD, PRIVACY_SETTINGS, API"
                    ))
            }
            is GrantConsentResult.NoChange -> {
                ResponseEntity.ok()
                    .body(mapOf(
                        "message" to "Consent already in requested state",
                        "consentType" to result.consentType.name,
                        "currentStatus" to result.currentStatus
                    ))
            }
            is GrantConsentResult.Failure -> {
                logger.error("Failed to process consent: {}", result.message, result.cause)
                ResponseEntity.internalServerError()
                    .body(mapOf("error" to "An internal error occurred"))
            }
        }
    }

    /**
     * Gets the current consent status for a customer.
     *
     * @param customerId The customer ID (UUID).
     * @param userId The user ID from the Authorization header (for authorization).
     * @return List of current consent statuses or appropriate error response.
     */
    @GetMapping("/{customerId}/consents")
    fun getConsents(
        @PathVariable customerId: String,
        @RequestHeader("X-User-Id") userId: String
    ): ResponseEntity<Any> {
        logger.debug("Getting consents for customer: {}", customerId)

        val parsedCustomerId = parseUUID(customerId) ?: run {
            logger.warn("Invalid customer ID format: {}", customerId)
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Invalid customer ID format"))
        }

        val parsedUserId = parseUUID(userId) ?: run {
            logger.warn("Invalid user ID format: {}", userId)
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Invalid user ID format"))
        }

        return when (val result = getConsentsUseCase.execute(
            customerId = parsedCustomerId,
            userId = parsedUserId
        )) {
            is GetConsentsResult.Success -> {
                val response = ConsentsListResponse(
                    customerId = result.customerId.toString(),
                    consents = result.consents.map { ConsentStatusResponse.fromDomain(it) }
                )
                ResponseEntity.ok(response)
            }
            is GetConsentsResult.CustomerNotFound -> {
                ResponseEntity.notFound().build()
            }
            is GetConsentsResult.Unauthorized -> {
                ResponseEntity.status(403)
                    .body(mapOf("error" to "You are not authorized to view this customer's consents"))
            }
            is GetConsentsResult.Failure -> {
                logger.error("Failed to get consents: {}", result.message, result.cause)
                ResponseEntity.internalServerError()
                    .body(mapOf("error" to "An internal error occurred"))
            }
        }
    }

    /**
     * Exports the consent history for a customer (GDPR data subject request).
     *
     * @param customerId The customer ID (UUID).
     * @param userId The user ID from the Authorization header (for authorization).
     * @param format The export format (currently only "json" is supported).
     * @return The consent history export or appropriate error response.
     */
    @GetMapping("/{customerId}/consents/history")
    fun exportConsentHistory(
        @PathVariable customerId: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestParam(required = false, defaultValue = "json") format: String
    ): ResponseEntity<Any> {
        logger.debug("Exporting consent history for customer: {} (format={})", customerId, format)

        // Validate format
        if (format.lowercase() != "json") {
            return ResponseEntity.badRequest()
                .body(mapOf(
                    "error" to "UNSUPPORTED_FORMAT",
                    "message" to "Currently only JSON format is supported"
                ))
        }

        val parsedCustomerId = parseUUID(customerId) ?: run {
            logger.warn("Invalid customer ID format: {}", customerId)
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Invalid customer ID format"))
        }

        val parsedUserId = parseUUID(userId) ?: run {
            logger.warn("Invalid user ID format: {}", userId)
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Invalid user ID format"))
        }

        return when (val result = exportConsentHistoryUseCase.execute(
            customerId = parsedCustomerId,
            userId = parsedUserId
        )) {
            is ExportConsentHistoryResult.Success -> {
                ResponseEntity.ok(ConsentHistoryExportResponse.fromDomain(result.export))
            }
            is ExportConsentHistoryResult.CustomerNotFound -> {
                ResponseEntity.notFound().build()
            }
            is ExportConsentHistoryResult.Unauthorized -> {
                ResponseEntity.status(403)
                    .body(mapOf("error" to "You are not authorized to export this customer's consent history"))
            }
            is ExportConsentHistoryResult.Failure -> {
                logger.error("Failed to export consent history: {}", result.message, result.cause)
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
}
