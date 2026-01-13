package com.acme.customer.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Type-safe wrapper for consent record identifiers.
 *
 * @property value The underlying UUID value.
 */
@JvmInline
value class ConsentId(val value: UUID) {
    override fun toString(): String = value.toString()
}

/**
 * Source of a consent change.
 *
 * Tracks where the consent was granted or revoked from.
 */
enum class ConsentSource {
    /** Consent granted during initial registration. */
    REGISTRATION,

    /** Consent updated via profile setup wizard. */
    PROFILE_WIZARD,

    /** Consent updated in privacy settings. */
    PRIVACY_SETTINGS,

    /** Consent updated via API. */
    API,

    /** Consent expired automatically. */
    SYSTEM_EXPIRATION,

    /** Consent change due to account closure. */
    ACCOUNT_CLOSURE
}

/**
 * JPA entity representing an immutable consent record.
 *
 * Consent records are append-only - they are never updated or deleted.
 * Each change to a customer's consent creates a new record with an
 * incremented version number. The current consent status can be
 * determined by finding the latest record for each consent type.
 *
 * This design ensures a complete audit trail for GDPR compliance
 * and allows for full consent history export.
 *
 * @property id Unique identifier for this consent record.
 * @property customerId The customer this consent belongs to.
 * @property consentType The type of consent.
 * @property granted Whether consent was granted (true) or revoked (false).
 * @property source Where the consent change originated.
 * @property ipAddress Client IP address when consent was changed.
 * @property userAgent Client user agent string when consent was changed.
 * @property expiresAt When this consent expires (null = never expires).
 * @property createdAt When this record was created (immutable).
 * @property version Incrementing version number for this consent type.
 */
@Entity
@Table(name = "consent_records")
class ConsentRecord(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "customer_id", nullable = false, updatable = false)
    val customerId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, updatable = false)
    val consentType: ConsentType,

    @Column(name = "granted", nullable = false, updatable = false)
    val granted: Boolean,

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false)
    val source: ConsentSource,

    @Column(name = "ip_address", nullable = false, updatable = false)
    val ipAddress: String,

    @Column(name = "user_agent", updatable = false)
    val userAgent: String? = null,

    @Column(name = "expires_at", updatable = false)
    val expiresAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "version", nullable = false, updatable = false)
    val version: Int
) {
    /**
     * Returns a type-safe ConsentId wrapper for this record.
     */
    fun getConsentId(): ConsentId = ConsentId(id)

    /**
     * Returns a type-safe CustomerId wrapper for this record.
     */
    fun getCustomerId(): CustomerId = CustomerId(customerId)

    /**
     * Checks if this consent has expired.
     *
     * @return true if the consent has an expiration date and it has passed.
     */
    fun isExpired(): Boolean {
        return expiresAt != null && Instant.now().isAfter(expiresAt)
    }

    /**
     * Checks if this consent is currently effective (granted and not expired).
     *
     * @return true if the consent is granted and not expired.
     */
    fun isEffective(): Boolean {
        return granted && !isExpired()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConsentRecord) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String {
        return "ConsentRecord(id=$id, customerId=$customerId, consentType=$consentType, " +
                "granted=$granted, source=$source, version=$version, createdAt=$createdAt)"
    }

    companion object {
        /** Default expiration period for non-required consents (1 year). */
        const val DEFAULT_EXPIRATION_DAYS: Long = 365

        /**
         * Creates a new consent record for granting or revoking consent.
         *
         * @param id The unique ID for this record.
         * @param customerId The customer ID.
         * @param consentType The type of consent.
         * @param granted Whether consent is being granted or revoked.
         * @param source Where the consent change originated.
         * @param ipAddress Client IP address.
         * @param userAgent Client user agent (optional).
         * @param expiresAt When the consent expires (optional).
         * @param version The version number for this consent type.
         * @return A new ConsentRecord instance.
         */
        fun create(
            id: UUID,
            customerId: UUID,
            consentType: ConsentType,
            granted: Boolean,
            source: ConsentSource,
            ipAddress: String,
            userAgent: String? = null,
            expiresAt: Instant? = null,
            version: Int
        ): ConsentRecord {
            return ConsentRecord(
                id = id,
                customerId = customerId,
                consentType = consentType,
                granted = granted,
                source = source,
                ipAddress = ipAddress,
                userAgent = userAgent,
                expiresAt = expiresAt,
                createdAt = Instant.now(),
                version = version
            )
        }

        /**
         * Creates an initial DATA_PROCESSING consent record at registration.
         *
         * @param id The unique ID for this record.
         * @param customerId The customer ID.
         * @param ipAddress Client IP address.
         * @param userAgent Client user agent (optional).
         * @return A new ConsentRecord for DATA_PROCESSING consent.
         */
        fun createInitialDataProcessingConsent(
            id: UUID,
            customerId: UUID,
            ipAddress: String,
            userAgent: String? = null
        ): ConsentRecord {
            return ConsentRecord(
                id = id,
                customerId = customerId,
                consentType = ConsentType.DATA_PROCESSING,
                granted = true,
                source = ConsentSource.REGISTRATION,
                ipAddress = ipAddress,
                userAgent = userAgent,
                expiresAt = null, // DATA_PROCESSING never expires
                createdAt = Instant.now(),
                version = 1
            )
        }
    }
}
