package com.acme.identity.infrastructure.persistence

import com.acme.identity.domain.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, UUID> {
    fun findByToken(token: String): PasswordResetToken?
    fun findByUserId(userId: UUID): List<PasswordResetToken>
    fun deleteByUserId(userId: UUID)
}
