package com.acme.identity.api.v1

import arrow.core.Either
import com.acme.identity.application.ResendVerificationUseCase
import com.acme.identity.application.VerificationError
import com.acme.identity.application.VerifyEmailUseCase
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

/**
 * Covers the verify redirects the integration test can't reach: the token table's
 * foreign key rules out an InternalError, and a real use case doesn't throw on demand.
 */
class VerificationControllerTest {
    private lateinit var verifyEmailUseCase: VerifyEmailUseCase
    private lateinit var controller: VerificationController

    @BeforeEach
    fun setUp() {
        verifyEmailUseCase = mockk()
        controller = VerificationController(
            verifyEmailUseCase = verifyEmailUseCase,
            resendVerificationUseCase = mockk<ResendVerificationUseCase>(),
            frontendBaseUrl = "https://www.acme.com"
        )
    }

    @Test
    fun `verify redirects to sign-in with a server error when the use case reports an internal error`() {
        every { verifyEmailUseCase.execute("tok", any()) } returns
            Either.Left(VerificationError.InternalError("user not found"))

        val response = controller.verifyEmail("tok", null)

        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertEquals("https://www.acme.com/signin?verify_error=error", response.headers.getFirst(HttpHeaders.LOCATION))
    }

    @Test
    fun `verify redirects to sign-in with a server error when the use case throws`() {
        every { verifyEmailUseCase.execute("tok", any()) } throws IllegalStateException("database unavailable")

        val response = controller.verifyEmail("tok", null)

        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertEquals("https://www.acme.com/signin?verify_error=error", response.headers.getFirst(HttpHeaders.LOCATION))
    }
}
