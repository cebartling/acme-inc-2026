package com.acme.cart.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

/**
 * Verifies identity's RS256 access tokens against its published JWKS: signature, `exp`,
 * `iss` and `aud`. Keys are fetched on first use and re-fetched when a token carries an
 * unknown `kid` (identity regenerates its keys on restart).
 */
@Configuration
class JwtDecoderConfig(
    @Value("\${acme.identity.jwks-uri}") private val jwksUri: String,
    @Value("\${acme.identity.issuer}") private val issuer: String,
    @Value("\${acme.identity.audience}") private val audience: String
) {
    @Bean
    fun jwtDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator()
            )
        )
        return decoder
    }

    private fun audienceValidator() = OAuth2TokenValidator<Jwt> { jwt ->
        if (jwt.audience?.contains(audience) == true) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "audience must include $audience", null))
        }
    }
}
