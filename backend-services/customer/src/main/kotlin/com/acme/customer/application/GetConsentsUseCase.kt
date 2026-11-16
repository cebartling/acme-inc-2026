package com.acme.customer.application

import com.acme.customer.domain.ConsentType
import com.acme.customer.infrastructure.projection.CustomerReadModelQueries
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
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
    private val readModel: CustomerReadModelQueries
) {
    private val logger = LoggerFactory.getLogger(GetConsentsUseCase::class.java)

    /**
     * Retrieves the current consent status for a customer.
     *
     * @param customerId The customer ID.
     * @param userId The user ID (for authorization).
     * @param readAfter The `X-Read-After` a write returned, to wait until that write is visible.
     * @return The result containing current consent statuses.
     * @throws com.acme.customer.infrastructure.projection.ReadModelBehindException if the read model has not caught up.
     */
    fun execute(
        customerId: UUID,
        userId: UUID,
        readAfter: UUID? = null
    ): GetConsentsResult {
        logger.debug("Getting consents for customer: {}", customerId)

        // Find customer. A read model that is behind must surface as an error, not as a failure result.
        val snapshot = readModel.findById(customerId, readAfter)
            ?: run {
                logger.debug("Customer not found: {}", customerId)
                return GetConsentsResult.CustomerNotFound(customerId)
            }

        // Authorization check
        if (snapshot.customer.userId != userId.toString()) {
            logger.warn(
                "User {} not authorized to view consents for customer {}",
                userId,
                customerId
            )
            return GetConsentsResult.Unauthorized(customerId, userId)
        }

        return try {
            // The current record for a type is its latest one
            val currentRecords = snapshot.consents
                .groupBy { it.consentType }
                .mapValues { (_, records) -> records.maxBy { it.createdAt } }

            // Build consent status list, including all consent types
            val consentStatuses = ConsentType.entries.map { consentType ->
                val record = currentRecords[consentType.name]

                if (record != null) {
                    CurrentConsentStatus(
                        consentType = consentType,
                        currentStatus = record.granted,
                        grantedAt = if (record.granted) record.createdAt else null,
                        expiresAt = record.expiresAt,
                        source = record.source,
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
