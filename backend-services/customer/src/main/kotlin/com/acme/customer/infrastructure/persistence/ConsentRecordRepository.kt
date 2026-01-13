package com.acme.customer.infrastructure.persistence

import com.acme.customer.domain.ConsentRecord
import com.acme.customer.domain.ConsentType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Repository for consent records.
 *
 * This repository provides append-only semantics for consent records.
 * Records are never updated or deleted - all changes are new inserts.
 * The current consent status can be determined by finding the latest
 * record for each consent type.
 */
@Repository
interface ConsentRecordRepository : JpaRepository<ConsentRecord, UUID> {

    /**
     * Finds all consent records for a customer.
     *
     * @param customerId The customer ID.
     * @return List of all consent records, ordered by creation time.
     */
    @Query("""
        SELECT cr FROM ConsentRecord cr
        WHERE cr.customerId = :customerId
        ORDER BY cr.createdAt ASC
    """)
    fun findByCustomerId(@Param("customerId") customerId: UUID): List<ConsentRecord>

    /**
     * Finds all consent records for a customer of a specific type.
     *
     * @param customerId The customer ID.
     * @param consentType The type of consent.
     * @return List of consent records for this type, ordered by creation time.
     */
    @Query("""
        SELECT cr FROM ConsentRecord cr
        WHERE cr.customerId = :customerId
        AND cr.consentType = :consentType
        ORDER BY cr.createdAt ASC
    """)
    fun findByCustomerIdAndConsentType(
        @Param("customerId") customerId: UUID,
        @Param("consentType") consentType: ConsentType
    ): List<ConsentRecord>

    /**
     * Finds the latest consent record for a customer and consent type.
     *
     * This represents the current consent status for that type.
     *
     * @param customerId The customer ID.
     * @param consentType The type of consent.
     * @return The latest consent record, or null if none exists.
     */
    @Query("""
        SELECT cr FROM ConsentRecord cr
        WHERE cr.customerId = :customerId
        AND cr.consentType = :consentType
        ORDER BY cr.createdAt DESC
        LIMIT 1
    """)
    fun findLatestByCustomerIdAndConsentType(
        @Param("customerId") customerId: UUID,
        @Param("consentType") consentType: ConsentType
    ): ConsentRecord?

    /**
     * Finds the current (latest) consent records for a customer.
     *
     * Returns one record per consent type, representing the current status.
     *
     * @param customerId The customer ID.
     * @return List of current consent records.
     */
    @Query(value = """
        SELECT DISTINCT ON (cr.consent_type)
            cr.id, cr.customer_id, cr.consent_type, cr.granted, cr.source,
            cr.ip_address, cr.user_agent, cr.expires_at, cr.created_at, cr.version
        FROM consent_records cr
        WHERE cr.customer_id = :customerId
        ORDER BY cr.consent_type, cr.created_at DESC
    """, nativeQuery = true)
    fun findCurrentConsentsByCustomerId(@Param("customerId") customerId: UUID): List<ConsentRecord>

    /**
     * Gets the current version number for a consent type.
     *
     * @param customerId The customer ID.
     * @param consentType The type of consent.
     * @return The current version number, or null if no consent exists.
     */
    @Query("""
        SELECT cr.version FROM ConsentRecord cr
        WHERE cr.customerId = :customerId
        AND cr.consentType = :consentType
        ORDER BY cr.createdAt DESC
        LIMIT 1
    """)
    fun getCurrentVersion(
        @Param("customerId") customerId: UUID,
        @Param("consentType") consentType: ConsentType
    ): Int?

    /**
     * Checks if a consent type has been granted (current status).
     *
     * @param customerId The customer ID.
     * @param consentType The type of consent.
     * @return True if the latest consent record is granted and not expired.
     */
    @Query("""
        SELECT CASE WHEN cr.granted = true AND (cr.expiresAt IS NULL OR cr.expiresAt > CURRENT_TIMESTAMP) THEN true ELSE false END
        FROM ConsentRecord cr
        WHERE cr.customerId = :customerId
        AND cr.consentType = :consentType
        ORDER BY cr.createdAt DESC
        LIMIT 1
    """)
    fun isConsentCurrentlyGranted(
        @Param("customerId") customerId: UUID,
        @Param("consentType") consentType: ConsentType
    ): Boolean?

    /**
     * Counts the total number of consent records for a customer.
     *
     * Used for GDPR export to report the total history size.
     *
     * @param customerId The customer ID.
     * @return The count of consent records.
     */
    fun countByCustomerId(customerId: UUID): Long

    /**
     * Deletes all consent records for a customer.
     *
     * This should only be called during account deletion (GDPR right to erasure).
     * Note: This is handled by CASCADE DELETE on the foreign key.
     *
     * @param customerId The customer ID.
     */
    fun deleteByCustomerId(customerId: UUID)
}
