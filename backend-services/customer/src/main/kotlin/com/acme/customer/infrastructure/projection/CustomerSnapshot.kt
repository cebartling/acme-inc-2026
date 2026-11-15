package com.acme.customer.infrastructure.projection

import com.acme.customer.api.v1.dto.AddressResponse
import com.acme.customer.api.v1.dto.CustomerResponse
import com.acme.customer.api.v1.dto.ProfileCompletenessResponse
import com.acme.customer.domain.ConsentRecord
import java.time.Instant
import java.util.UUID

/**
 * Everything the customer read endpoints return, as of one moment.
 *
 * It is the body of the MongoDB read model document. It reuses the API response types, so a read
 * is deserialization with no mapping. It holds PII (date of birth, phone, addresses, consent IP
 * addresses), so it travels only on [CustomerSnapshotMessage.TOPIC], which only the projector
 * consumes, never on `customer.events`.
 */
data class CustomerSnapshot(
    val customer: CustomerResponse,
    /** When the preferences last changed; `GET /{id}/preferences` returns it. */
    val preferencesUpdatedAt: Instant,
    val addresses: List<AddressResponse>,
    val consents: List<ConsentRecordSnapshot>,
    val completeness: ProfileCompletenessResponse
)

/** One row of the append-only consent history (ADR 0028), including what the history endpoint shows. */
data class ConsentRecordSnapshot(
    val consentId: String,
    val consentType: String,
    val granted: Boolean,
    val source: String,
    val ipAddress: String,
    val userAgent: String?,
    val expiresAt: Instant?,
    val createdAt: Instant,
    val version: Int
) {
    companion object {
        fun fromDomain(record: ConsentRecord) = ConsentRecordSnapshot(
            consentId = record.id.toString(),
            consentType = record.consentType.name,
            granted = record.granted,
            source = record.source.name,
            ipAddress = record.ipAddress,
            userAgent = record.userAgent,
            expiresAt = record.expiresAt,
            createdAt = record.createdAt,
            version = record.version
        )
    }
}

/**
 * The outbox payload for `customer.read-model`.
 *
 * [sourceEventId] is the `customer.events` event that caused this snapshot. A write returns it, and a
 * read can wait until the read model has applied it.
 */
data class CustomerSnapshotMessage(
    val sourceEventId: UUID,
    val customerId: UUID,
    val snapshot: CustomerSnapshot
) {
    companion object {
        const val TOPIC = "customer.read-model"
        const val EVENT_TYPE = "CustomerSnapshot"
    }
}
