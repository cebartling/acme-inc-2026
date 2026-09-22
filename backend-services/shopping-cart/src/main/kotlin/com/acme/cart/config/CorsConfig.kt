package com.acme.cart.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig(
    @Value("\${acme.cors.extra-origins:}") private val extraOrigins: String
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(
                "http://localhost:3000",
                "http://localhost:7600",
                "http://localhost:5173",
                "http://localhost:5174",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:7600",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:5174",
                *parseOrigins(extraOrigins)
            )
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600)
    }

    /** Parses a comma-separated list of origins, e.g. a Tailscale serve URL. */
    private fun parseOrigins(origins: String): Array<String> =
        origins.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
}
