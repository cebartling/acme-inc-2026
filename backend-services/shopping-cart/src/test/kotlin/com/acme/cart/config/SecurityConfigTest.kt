package com.acme.cart.config

import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityConfigTest {

    private val validator = SecurityConfig().accessTokenValidator()

    private fun token(subject: String? = UUID.randomUUID().toString(), tokenUse: String? = "access"): Jwt {
        val builder = Jwt.withTokenValue("t").header("alg", "RS256").claim("iss", "https://auth.acme.com")
        subject?.let(builder::subject)
        tokenUse?.let { builder.claim(SecurityConfig.TOKEN_USE_CLAIM, it) }
        return builder.build()
    }

    @Test
    fun `an access token for a user ID is accepted`() {
        assertFalse(validator.validate(token()).hasErrors())
    }

    @Test
    fun `a refresh token is rejected`() {
        assertTrue(validator.validate(token(tokenUse = "refresh")).hasErrors())
    }

    @Test
    fun `a token without token_use is rejected`() {
        assertTrue(validator.validate(token(tokenUse = null)).hasErrors())
    }

    @Test
    fun `a subject that is not a user ID is rejected`() {
        assertTrue(validator.validate(token(subject = "not-a-uuid")).hasErrors())
        assertTrue(validator.validate(token(subject = null)).hasErrors())
    }
}
