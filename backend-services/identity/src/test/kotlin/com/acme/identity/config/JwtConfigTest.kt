package com.acme.identity.config

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JwtConfigTest {

    @Test
    fun `toString never prints the signing key`() {
        val secret = "-----BEGIN PRIVATE KEY-----\nTOPSECRETKEYMATERIAL\n-----END PRIVATE KEY-----\n"

        val printed = JwtConfig(signingKey = secret).toString()

        assertFalse(printed.contains("TOPSECRETKEYMATERIAL"), printed)
        assertTrue(printed.contains("signingKey=<redacted>"), printed)
    }

    @Test
    fun `toString says when no signing key is configured`() {
        assertTrue(JwtConfig().toString().contains("signingKey=<none>"))
    }

    @Test
    fun `toString still shows the other settings`() {
        val printed = JwtConfig(issuer = "https://auth.example.com", keyRotationPeriodDays = 7).toString()

        assertTrue(printed.contains("issuer=https://auth.example.com"), printed)
        assertTrue(printed.contains("keyRotationPeriodDays=7"), printed)
    }
}
