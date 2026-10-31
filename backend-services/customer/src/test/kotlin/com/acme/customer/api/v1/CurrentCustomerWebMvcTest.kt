package com.acme.customer.api.v1

import com.acme.customer.application.GetCustomerProfileResult
import com.acme.customer.application.GetCustomerProfileUseCase
import com.acme.customer.application.ProfileCompletenessCalculator
import com.acme.customer.application.UpdateProfileUseCase
import com.acme.customer.config.SecurityConfig
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.options
import java.util.UUID

/**
 * `GET /api/v1/customers/me` through the real security chain: who the customer is comes from
 * the verified `access_token` cookie, never from a header the caller chose.
 */
@WebMvcTest(CustomerController::class)
@Import(SecurityConfig::class)
class CurrentCustomerWebMvcTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val getProfile: GetCustomerProfileUseCase,
    @Autowired private val jwtDecoder: JwtDecoder,
    @Autowired private val accessTokenValidator: OAuth2TokenValidator<Jwt>
) {

    @TestConfiguration
    class Beans {
        @Bean
        fun customerRepository(): CustomerRepository = mockk()

        @Bean
        fun customerPreferencesRepository(): CustomerPreferencesRepository = mockk()

        @Bean
        fun updateProfileUseCase(): UpdateProfileUseCase = mockk()

        @Bean
        fun profileCompletenessCalculator(): ProfileCompletenessCalculator = mockk()

        @Bean
        fun getCustomerProfileUseCase(): GetCustomerProfileUseCase = mockk()

        /** Stands in for the JWKS-backed decoder; tests decide what a token decodes to. */
        @Bean
        fun jwtDecoder(): JwtDecoder = mockk()
    }

    private val path = SecurityConfig.CURRENT_CUSTOMER_PATH
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        clearMocks(getProfile, jwtDecoder)
        every { jwtDecoder.decode("good-token") } returns jwt(subject = userId.toString())
        every { getProfile.execute(any()) } returns GetCustomerProfileResult.NotFound(userId)
    }

    private fun jwt(subject: String, tokenUse: String = SecurityConfig.TOKEN_USE_ACCESS): Jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject(subject)
            .claim(SecurityConfig.TOKEN_USE_CLAIM, tokenUse)
            .build()

    private fun accessToken(value: String = "good-token") = Cookie(SecurityConfig.ACCESS_TOKEN_COOKIE, value)

    private fun rejectToken(value: String, description: String) {
        every { jwtDecoder.decode(value) } throws
            JwtValidationException(description, listOf(OAuth2Error("invalid_token", description, null)))
    }

    @Test
    fun `a customer with no profile is a 404 for the token's subject`() {
        mockMvc.get(path) { cookie(accessToken()) }.andExpect { status { isNotFound() } }

        verify(exactly = 1) { getProfile.execute(userId) }
    }

    @Test
    fun `an X-User-Id header doesn't pick another customer`() {
        mockMvc.get(path) {
            cookie(accessToken())
            header("X-User-Id", UUID.randomUUID().toString())
        }.andExpect { status { isNotFound() } }

        verify(exactly = 1) { getProfile.execute(userId) }
    }

    @Test
    fun `an X-User-Id header without a cookie is SIGN_IN_REQUIRED`() {
        mockMvc.get(path) { header("X-User-Id", userId.toString()) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("SIGN_IN_REQUIRED") }
        }

        verify(exactly = 0) { getProfile.execute(any()) }
    }

    @Test
    fun `an expired token is TOKEN_EXPIRED, which the app refreshes on`() {
        rejectToken("old-token", "Jwt expired at 2026-10-31T00:00:00Z")

        mockMvc.get(path) { cookie(accessToken("old-token")) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("TOKEN_EXPIRED") }
        }
    }

    @Test
    fun `a token that fails verification is INVALID_TOKEN`() {
        rejectToken("bad-token", "Signature mismatch")

        mockMvc.get(path) { cookie(accessToken("bad-token")) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("INVALID_TOKEN") }
        }
    }

    @Test
    fun `a refresh token isn't an access token`() {
        assertTrue(accessTokenValidator.validate(jwt(userId.toString(), tokenUse = "refresh")).hasErrors())
    }

    @Test
    fun `a subject that isn't a user ID is rejected`() {
        assertTrue(accessTokenValidator.validate(jwt("not-a-uuid")).hasErrors())
    }

    @Test
    fun `an access token for a user passes`() {
        assertFalse(accessTokenValidator.validate(jwt(userId.toString())).hasErrors())
    }

    @Test
    fun `the customer app's preflight is answered without a token`() {
        mockMvc.options(path) {
            header("Origin", "http://localhost:7600")
            header("Access-Control-Request-Method", "GET")
        }.andExpect {
            status { isOk() }
            header { string("Access-Control-Allow-Credentials", "true") }
        }
    }

    @Test
    fun `other customer endpoints don't check the access cookie`() {
        // Inside the chain this cookie would be a 401; the controller answers the bad ID instead
        mockMvc.get("/api/v1/customers/by-user/not-a-uuid") {
            cookie(accessToken("bad-token"))
        }.andExpect { status { isBadRequest() } }
    }
}
