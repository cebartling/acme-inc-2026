package com.acme.order.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.config.annotation.CorsRegistry

class CorsConfigTest {

    private class Registry : CorsRegistry() {
        fun configurations() = corsConfigurations
    }

    @Test
    fun `exposes X-Cart-Read-After, which a browser would otherwise hide from the customer app (PIN-551)`() {
        val registry = Registry()

        CorsConfig(extraOrigins = "").addCorsMappings(registry)

        assertEquals(listOf("X-Cart-Read-After"), registry.configurations().getValue("/**").exposedHeaders)
    }
}
