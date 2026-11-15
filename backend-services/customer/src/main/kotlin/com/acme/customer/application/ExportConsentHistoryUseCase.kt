package com.acme.customer.application

import com.acme.customer.infrastructure.projection.CustomerReadModelQueries
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Represents a consent history entry for GDPR export.
 */
data class ConsentHistoryEntry(
    val consentId: UUID,
    val consentType: String,
    val granted: Boolean,
    val timestamp: Instant,
    val source: String,
    val ipAddress: String,
    val userAgent: String?
)

/**
 * Represents the full consent history export for GDPR data subject requests.
 */
data class ConsentHistoryExport(
    val customerId: UUID,
    val exportedAt: Instant,
    val totalRecords: Int,
    val consentHistory: List<ConsentHistoryEntry>
)

/**
 * Result type for the ExportConsentHistoryUseCase.
 */
sealed class ExportConsentHistoryResult {
    /**
     * Successfully exported consent history.
     */
    data class Success(
        val export: ConsentHistoryExport
    ) : ExportConsentHistoryResult()

    /**
     * Customer not found.
     */
    data class CustomerNotFound(val customerId: UUID) : ExportConsentHistoryResult()

    /**
     * User is not authorized to export this customer's consent history.
     */
    data class Unauthorized(val customerId: UUID, val userId: UUID) : ExportConsentHistoryResult()

    /**
     * Unexpected failure.
     */
    data class Failure(val message: String, val cause: Throwable? = null) : ExportConsentHistoryResult()
}

/**
 * Use case for exporting a customer's complete consent history.
 *
 * This use case supports GDPR data subject requests (Article 15 - Right of Access)
 * by providing a complete, chronological export of all consent records.
 *
 * The export includes:
 * - All consent grants and revocations
 * - Timestamps for each change
 * - Source of each change (where it came from)
 * - IP address and user agent for audit purposes
 */
@Service
class ExportConsentHistoryUseCase(
    private val readModel: CustomerReadModelQueries,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(ExportConsentHistoryUseCase::class.java)

    private lateinit var consentExportCounter: Counter

    @PostConstruct
    fun initMetrics() {
        consentExportCounter = Counter.builder("consent_export_total")
            .description("Total number of consent history exports")
            .register(meterRegistry)
    }

    /**
     * Exports the complete consent history for a customer.
     *
     * @param customerId The customer ID.
     * @param userId The user ID (for authorization).
     * @return The result containing the consent history export.
     */
    fun execute(
        customerId: UUID,
        userId: UUID,
        readAfter: UUID? = null
    ): ExportConsentHistoryResult {
        logger.debug("Exporting consent history for customer: {}", customerId)

        // Find customer. A read model that is behind must surface as an error, not as a failure result.
        val snapshot = readModel.findById(customerId, readAfter)
            ?: run {
                logger.debug("Customer not found: {}", customerId)
                return ExportConsentHistoryResult.CustomerNotFound(customerId)
            }

        // Authorization check
        if (snapshot.customer.userId != userId.toString()) {
            logger.warn(
                "User {} not authorized to export consent history for customer {}",
                userId,
                customerId
            )
            return ExportConsentHistoryResult.Unauthorized(customerId, userId)
        }

        return try {
            // Get all consent records in chronological order
            val consentRecords = snapshot.consents.sortedBy { it.createdAt }

            // Convert to history entries
            val historyEntries = consentRecords.map { record ->
                ConsentHistoryEntry(
                    consentId = UUID.fromString(record.consentId),
                    consentType = record.consentType,
                    granted = record.granted,
                    timestamp = record.createdAt,
                    source = record.source,
                    ipAddress = record.ipAddress,
                    userAgent = record.userAgent
                )
            }

            val export = ConsentHistoryExport(
                customerId = customerId,
                exportedAt = Instant.now(),
                totalRecords = historyEntries.size,
                consentHistory = historyEntries
            )

            // Track metric
            consentExportCounter.increment()

            logger.info(
                "Exported {} consent records for customer {}",
                historyEntries.size,
                customerId
            )

            ExportConsentHistoryResult.Success(export)
        } catch (e: Exception) {
            logger.error(
                "Failed to export consent history for customer {}: {}",
                customerId,
                e.message,
                e
            )
            ExportConsentHistoryResult.Failure("Failed to export consent history: ${e.message}", e)
        }
    }
}
