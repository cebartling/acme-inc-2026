package com.acme.cart.api.v1

import com.acme.cart.config.SecurityConfig
import com.acme.cart.infrastructure.persistence.CartRepository
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID
import kotlin.test.assertTrue

class TestHelperControllerWebMvcTest {

    @TestConfiguration
    class Beans {
        @Bean
        fun cartRepository(): CartRepository = mockk()

        @Bean
        fun jwtDecoder(): JwtDecoder = mockk()
    }

    @Nested
    @WebMvcTest(TestHelperController::class)
    @ActiveProfiles("test")
    @Import(SecurityConfig::class, GuestSessionCookies::class, Beans::class)
    inner class WithTheTestProfile(
        @Autowired private val mockMvc: MockMvc,
        @Autowired private val cartRepository: CartRepository
    ) {
        private val sessionId = UUID.randomUUID()

        @BeforeEach
        fun reset() = clearMocks(cartRepository)

        @Test
        fun `lapsing a live session backdates its expiry and answers 204`() {
            val expiresAt = slot<Instant>()
            every { cartRepository.lapseCheckoutSession(sessionId, capture(expiresAt)) } returns 1

            mockMvc.post("/api/v1/test/checkout-sessions/$sessionId/lapse")
                .andExpect { status { isNoContent() } }

            assertTrue(expiresAt.captured.isBefore(Instant.now()))
        }

        @Test
        fun `an unknown session is a 404 CHECKOUT_SESSION_NOT_FOUND`() {
            every { cartRepository.lapseCheckoutSession(sessionId, any()) } returns 0

            mockMvc.post("/api/v1/test/checkout-sessions/$sessionId/lapse").andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("CHECKOUT_SESSION_NOT_FOUND") }
            }
        }
    }

    @Nested
    @WebMvcTest(TestHelperController::class)
    @Import(SecurityConfig::class, GuestSessionCookies::class, Beans::class)
    inner class WithoutTheTestProfile(
        @Autowired private val mockMvc: MockMvc,
        @Autowired private val cartRepository: CartRepository
    ) {
        @Test
        fun `the endpoint doesn't exist`() {
            mockMvc.post("/api/v1/test/checkout-sessions/${UUID.randomUUID()}/lapse")
                .andExpect { status { isNotFound() } }

            verify(exactly = 0) { cartRepository.lapseCheckoutSession(any(), any()) }
        }
    }
}
