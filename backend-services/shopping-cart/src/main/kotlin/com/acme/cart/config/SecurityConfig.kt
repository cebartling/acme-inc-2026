package com.acme.cart.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import java.util.UUID

/**
 * Optional authentication for the cart API (US-0004-08).
 *
 * Every endpoint stays open to guests. When the browser sends identity's `access_token`
 * cookie, the token is verified (Spring Boot's resource server, configured under
 * `spring.security.oauth2.resourceserver.jwt`, plus [accessTokenValidator]) and the caller
 * is that user; the controller then acts on their user cart instead of the session's.
 *
 * CSRF protection is off: the service is a JSON API, state-changing requests need a JSON
 * content type (so a cross-site form cannot send one without a CORS preflight), and the
 * `access_token` cookie is SameSite=Strict.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .oauth2ResourceServer { resourceServer ->
                resourceServer
                    .bearerTokenResolver(accessTokenCookieResolver())
                    .authenticationEntryPoint(tokenErrorEntryPoint())
                    .jwt(Customizer.withDefaults())
            }
        return http.build()
    }

    /**
     * Beyond signature, `exp`, `iss` and `aud`: the token must be an access token (identity
     * signs refresh tokens with the same key, marked by `token_use`) and its subject must be
     * a user ID, so a bad token is a 401 rather than a failure in the controller.
     */
    @Bean
    fun accessTokenValidator(): OAuth2TokenValidator<Jwt> = OAuth2TokenValidator { jwt ->
        when {
            jwt.getClaimAsString(TOKEN_USE_CLAIM) != TOKEN_USE_ACCESS ->
                invalid("$TOKEN_USE_CLAIM must be $TOKEN_USE_ACCESS")
            jwt.subject?.let { runCatching { UUID.fromString(it) }.isSuccess } != true ->
                invalid("sub must be a user ID")
            else -> OAuth2TokenValidatorResult.success()
        }
    }

    private fun invalid(description: String) =
        OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", description, null))

    /** Reads the token from identity's HttpOnly `access_token` cookie; no header is used. */
    private fun accessTokenCookieResolver() = BearerTokenResolver { request: HttpServletRequest ->
        request.cookies?.firstOrNull { it.name == ACCESS_TOKEN_COOKIE }?.value?.takeIf { it.isNotBlank() }
    }

    /**
     * A token that fails verification is a 401. An expired one reports `TOKEN_EXPIRED`,
     * which the customer app's API client answers by refreshing and retrying.
     */
    private fun tokenErrorEntryPoint() = AuthenticationEntryPoint { _, response, exception: AuthenticationException ->
        val code = if (exception.isExpiry()) "TOKEN_EXPIRED" else "INVALID_TOKEN"
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write("""{"error":"$code"}""")
    }

    private fun Throwable.isExpiry(): Boolean =
        generateSequence(this) { it.cause }.any { it.message?.contains("expired", ignoreCase = true) == true }

    companion object {
        const val ACCESS_TOKEN_COOKIE = "access_token"
        const val TOKEN_USE_CLAIM = "token_use"
        const val TOKEN_USE_ACCESS = "access"
    }
}
