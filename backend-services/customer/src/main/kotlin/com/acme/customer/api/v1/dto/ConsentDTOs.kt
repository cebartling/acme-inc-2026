package com.acme.customer.api.v1.dto

import com.acme.customer.application.ConsentHistoryEntry
import com.acme.customer.application.ConsentHistoryExport
import com.acme.customer.application.CurrentConsentStatus
import com.acme.customer.domain.ConsentRecord
import java.time.Instant
import java.util.UUID

/**
 * Request to grant or revoke consent.
 *
 * @property consentType The type of consent (DATA_PROCESSING, MARKETING, ANALYTICS, THIRD_PARTY, PERSONALIZATION).
 * @property granted Whether to grant (true) or revoke (false) the consent.
 * @property source Where the consent change originated (REGISTRATION, PROFILE_WIZARD, PRIVACY_SETTINGS, API).
 * @property ipAddress The client's IP address (for audit).
 * @property userAgent The client's user agent (for audit).
 */
data class GrantConsentRequest(
    val consentType: String,
    val granted: Boolean,
    val source: String,
    val ipAddress: String,
    val userAgent: String? = null
)

/**
 * Response after successfully granting or revoking consent.
 *
 * @property consentId The unique ID of the consent record.
 * @property customerId The customer ID.
 * @property consentType The type of consent.
 * @property granted Whether the consent was granted or revoked.
 * @property grantedAt When the consent was granted (null if revoked).
 * @property source Where the consent change originated.
 * @property expiresAt When the consent expires (null if never).
 * @property version The version number of this consent type.
 */
data class ConsentResponse(
    val consentId: String,
    val customerId: String,
    val consentType: String,
    val granted: Boolean,
    val grantedAt: Instant?,
    val source: String,
    val expiresAt: Instant?,
    val version: Int
) {
    companion object {
        /**
         * Creates a response from a domain ConsentRecord.
         */
        fun fromDomain(record: ConsentRecord): ConsentResponse {
            return ConsentResponse(
                consentId = record.id.toString(),
                customerId = record.customerId.toString(),
                consentType = record.consentType.name,
                granted = record.granted,
                grantedAt = if (record.granted) record.createdAt else null,
                source = record.source.name,
                expiresAt = record.expiresAt,
                version = record.version
            )
        }
    }
}

/**
 * Response for a single consent status.
 *
 * @property consentType The type of consent.
 * @property currentStatus Whether the consent is currently granted.
 * @property grantedAt When the consent was last granted (null if never granted).
 * @property expiresAt When the consent expires (null if never).
 * @property source Where the last consent change originated.
 * @property required Whether this consent is required for service delivery.
 */
data class ConsentStatusResponse(
    val consentType: String,
    val currentStatus: Boolean,
    val grantedAt: Instant?,
    val expiresAt: Instant?,
    val source: String,
    val required: Boolean
) {
    companion object {
        /**
         * Creates a response from a CurrentConsentStatus.
         */
        fun fromDomain(status: CurrentConsentStatus): ConsentStatusResponse {
            return ConsentStatusResponse(
                consentType = status.consentType.name,
                currentStatus = status.currentStatus,
                grantedAt = status.grantedAt,
                expiresAt = status.expiresAt,
                source = status.source,
                required = status.required
            )
        }
    }
}

/**
 * Response containing all consent statuses for a customer.
 *
 * @property customerId The customer ID.
 * @property consents List of consent statuses.
 */
data class ConsentsListResponse(
    val customerId: String,
    val consents: List<ConsentStatusResponse>
)

/**
 * Response for a single consent history entry (for GDPR export).
 *
 * @property consentId The unique ID of the consent record.
 * @property consentType The type of consent.
 * @property granted Whether consent was granted or revoked.
 * @property timestamp When the consent change occurred.
 * @property source Where the consent change originated.
 * @property ipAddress The client's IP address when the change was made.
 * @property userAgent The client's user agent when the change was made.
 */
data class ConsentHistoryEntryResponse(
    val consentId: String,
    val consentType: String,
    val granted: Boolean,
    val timestamp: Instant,
    val source: String,
    val ipAddress: String,
    val userAgent: String?
) {
    companion object {
        /**
         * Creates a response from a ConsentHistoryEntry.
         */
        fun fromDomain(entry: ConsentHistoryEntry): ConsentHistoryEntryResponse {
            return ConsentHistoryEntryResponse(
                consentId = entry.consentId.toString(),
                consentType = entry.consentType,
                granted = entry.granted,
                timestamp = entry.timestamp,
                source = entry.source,
                ipAddress = entry.ipAddress,
                userAgent = entry.userAgent
            )
        }
    }
}

/**
 * Response containing the full consent history export for GDPR requests.
 *
 * @property customerId The customer ID.
 * @property exportedAt When the export was generated.
 * @property consentHistory List of all consent history entries.
 */
data class ConsentHistoryExportResponse(
    val customerId: String,
    val exportedAt: Instant,
    val consentHistory: List<ConsentHistoryEntryResponse>
) {
    companion object {
        /**
         * Creates a response from a ConsentHistoryExport.
         */
        fun fromDomain(export: ConsentHistoryExport): ConsentHistoryExportResponse {
            return ConsentHistoryExportResponse(
                customerId = export.customerId.toString(),
                exportedAt = export.exportedAt,
                consentHistory = export.consentHistory.map { ConsentHistoryEntryResponse.fromDomain(it) }
            )
        }
    }
}
