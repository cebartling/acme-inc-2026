package com.acme.customer.application

import com.acme.customer.domain.ConsentRecord
import com.acme.customer.domain.ConsentType
import com.acme.customer.infrastructure.persistence.ConsentRecordRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Represents the current consent status for a consent type.
 */
data class CurrentConsentStatus(
    val consentType: ConsentType,
    val currentStatus: Boolean,
    val grantedAt: Instant?,
    val expiresAt: Instant?,
    val source: String,
    val required: Boolean,
    val version: Int
) {
    /**
     * Checks if the consent is currently effective.
     */
    fun isEffective(): Boolean {
        return currentStatus && !isExpired()
    }

    /**
     * Checks if the consent has expired.
     */
    fun isExpired(): Boolean {
        return expiresAt != null && Instant.now().isAfter(expiresAt)
    }
}

/**
 * Result type for the GetConsentsUseCase.
 */
sealed class GetConsentsResult {
    /**
     * Successfully retrieved consents.
     */
    data class Success(
        val customerId: UUID,
        val consents: List<CurrentConsentStatus>
    ) : GetConsentsResult()

    /**
     * Customer not found.
     */
    data class CustomerNotFound(val customerId: UUID) : GetConsentsResult()

    /**
     * User is not authorized to view this customer's consents.
     */
    data class Unauthorized(val customerId: UUID, val userId: UUID) : GetConsentsResult()

    /**
     * Unexpected failure.
     */
    data class Failure(val message: String, val cause: Throwable? = null) : GetConsentsResult()
}

/**
 * Use case for retrieving a customer's current consent status.
 *
 * This use case retrieves the current consent status for all consent types,
 * showing whether each consent is granted, when it was granted, and when
 * it expires (if applicable).
 */
@Service
class GetConsentsUseCase(
    private val customerRepository: CustomerRepository,
    private val consentRecordRepository: ConsentRecordRepository
) {
    private val logger = LoggerFactory.getLogger(GetConsentsUseCase::class.java)

    /**
     * Retrieves the current consent status for a customer.
     *
     * @param customerId The customer ID.
     * @param userId The user ID (for authorization).
     * @return The result containing current consent statuses.
     */
    @Transactional(readOnly = true)
    fun execute(
        customerId: UUID,
        userId: UUID
    ): GetConsentsResult {
        logger.debug("Getting consents for customer: {}", customerId)

        // Find customer
        val customer = customerRepository.findById(customerId).orElse(null)
            ?: run {
                logger.debug("Customer not found: {}", customerId)
                return GetConsentsResult.CustomerNotFound(customerId)
            }

        // Authorization check
        if (customer.userId != userId) {
            logger.warn(
                "User {} not authorized to view consents for customer {}",
                userId,
                customerId
            )
            return GetConsentsResult.Unauthorized(customerId, userId)
        }

        return try {
            // Get current consent records
            val currentRecords = consentRecordRepository.findCurrentConsentsByCustomerId(customerId)

            // Build consent status list, including all consent types
            val consentStatuses = ConsentType.entries.map { consentType ->
                val record = currentRecords.find { it.consentType == consentType }

                if (record != null) {
                    CurrentConsentStatus(
                        consentType = consentType,
                        currentStatus = record.granted,
                        grantedAt = if (record.granted) record.createdAt else null,
                        expiresAt = record.expiresAt,
                        source = record.source.name,
                        required = consentType.required,
                        version = record.version
                    )
                } else {
                    // No record exists - consent not granted
                    CurrentConsentStatus(
                        consentType = consentType,
                        currentStatus = false,
                        grantedAt = null,
                        expiresAt = null,
                        source = "NONE",
                        required = consentType.required,
                        version = 0
                    )
                }
            }

            logger.debug(
                "Retrieved {} consent statuses for customer {}",
                consentStatuses.size,
                customerId
            )

            GetConsentsResult.Success(customerId, consentStatuses)
        } catch (e: Exception) {
            logger.error(
                "Failed to retrieve consents for customer {}: {}",
                customerId,
                e.message,
                e
            )
            GetConsentsResult.Failure("Failed to retrieve consents: ${e.message}", e)
        }
    }
}
