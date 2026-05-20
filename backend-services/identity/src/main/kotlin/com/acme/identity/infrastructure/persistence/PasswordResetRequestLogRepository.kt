package com.acme.identity.infrastructure.persistence

import com.acme.identity.domain.PasswordResetRequestLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface PasswordResetRequestLogRepository : JpaRepository<PasswordResetRequestLog, UUID> {

    @Query(
        "SELECT COUNT(r) FROM PasswordResetRequestLog r " +
            "WHERE r.email = :email AND r.requestedAt >= :since"
    )
    fun countByEmailSince(email: String, since: Instant): Long

    @Query(
        "SELECT r FROM PasswordResetRequestLog r " +
            "WHERE r.email = :email AND r.requestedAt >= :since " +
            "ORDER BY r.requestedAt ASC LIMIT 1"
    )
    fun findOldestByEmailSince(email: String, since: Instant): PasswordResetRequestLog?

    @Modifying
    @Query("DELETE FROM PasswordResetRequestLog r WHERE r.requestedAt < :before")
    fun deleteOlderThan(before: Instant)

    @Modifying
    @Query("DELETE FROM PasswordResetRequestLog r WHERE r.email = :email")
    fun deleteByEmail(email: String)
}
