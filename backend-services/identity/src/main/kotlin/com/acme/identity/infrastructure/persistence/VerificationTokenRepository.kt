package com.acme.identity.infrastructure.persistence

import com.acme.identity.domain.VerificationToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface VerificationTokenRepository : JpaRepository<VerificationToken, UUID> {
    fun findByToken(token: String): VerificationToken?
    fun findByUserId(userId: UUID): List<VerificationToken>
}
