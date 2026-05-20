package com.acme.identity.application

import com.acme.identity.domain.PasswordResetToken
import com.acme.identity.infrastructure.persistence.PasswordResetTokenRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidatePasswordResetTokenUseCaseTest {
    private val tokenRepository: PasswordResetTokenRepository = mockk()
    private val useCase = ValidatePasswordResetTokenUseCase(tokenRepository)

    @Test
    fun `Valid for fresh unused token`() {
        val token = PasswordResetToken(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            token = "rst_fresh",
            expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES)
        )
        every { tokenRepository.findByToken("rst_fresh") } returns token

        val result = useCase.execute("rst_fresh") as PasswordResetTokenValidation.Valid
        assertTrue(result.expiresInSeconds > 0)
    }

    @Test
    fun `Invalid when token not found`() {
        every { tokenRepository.findByToken("missing") } returns null
        assertEquals(PasswordResetTokenValidation.Invalid, useCase.execute("missing"))
    }

    @Test
    fun `Expired when expiry is past`() {
        val token = PasswordResetToken(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            token = "rst_expired",
            expiresAt = Instant.now().minus(1, ChronoUnit.MINUTES)
        )
        every { tokenRepository.findByToken("rst_expired") } returns token
        assertEquals(PasswordResetTokenValidation.Expired, useCase.execute("rst_expired"))
    }

    @Test
    fun `AlreadyUsed when usedAt is non-null`() {
        val token = PasswordResetToken(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            token = "rst_used",
            expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES),
            usedAt = Instant.now().minus(1, ChronoUnit.MINUTES)
        )
        every { tokenRepository.findByToken("rst_used") } returns token
        assertEquals(PasswordResetTokenValidation.AlreadyUsed, useCase.execute("rst_used"))
    }
}
