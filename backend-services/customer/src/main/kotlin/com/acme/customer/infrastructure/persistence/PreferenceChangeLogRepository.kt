package com.acme.customer.infrastructure.persistence

import com.acme.customer.domain.PreferenceChangeLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Spring Data JPA repository for [PreferenceChangeLog] entities.
 *
 * Provides methods for querying preference change audit logs.
 */
@Repository
interface PreferenceChangeLogRepository : JpaRepository<PreferenceChangeLog, UUID> {
    /**
     * Finds all preference change logs for a customer.
     *
     * @param customerId The customer ID.
     * @return List of change logs ordered by changedAt descending.
     */
    fun findByCustomerIdOrderByChangedAtDesc(customerId: UUID): List<PreferenceChangeLog>

    /**
     * Finds preference change logs for a customer within a time range.
     *
     * @param customerId The customer ID.
     * @param startTime Start of the time range (inclusive).
     * @param endTime End of the time range (exclusive).
     * @return List of change logs ordered by changedAt descending.
     */
    fun findByCustomerIdAndChangedAtBetweenOrderByChangedAtDesc(
        customerId: UUID,
        startTime: Instant,
        endTime: Instant
    ): List<PreferenceChangeLog>

    /**
     * Finds change logs for a specific preference.
     *
     * @param customerId The customer ID.
     * @param preferenceName The preference name (dot notation).
     * @return List of change logs ordered by changedAt descending.
     */
    fun findByCustomerIdAndPreferenceNameOrderByChangedAtDesc(
        customerId: UUID,
        preferenceName: String
    ): List<PreferenceChangeLog>
}
