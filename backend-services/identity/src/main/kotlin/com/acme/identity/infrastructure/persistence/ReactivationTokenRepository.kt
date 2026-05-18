package com.acme.identity.infrastructure.persistence

import com.acme.identity.domain.ReactivationToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ReactivationTokenRepository : JpaRepository<ReactivationToken, UUID> {
    fun findByToken(token: String): ReactivationToken?
    fun findByUserId(userId: UUID): List<ReactivationToken>
    fun deleteByUserId(userId: UUID)
}
