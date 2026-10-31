package com.acme.customer.config

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
 * Verifies identity's `access_token` cookie on `GET /api/v1/customers/me`, as the cart and order
 * services do (ADR-0043): Spring Boot's resource server, configured under
 * `spring.security.oauth2.resourceserver.jwt`, plus [accessTokenValidator].
 *
 * The chain matches that path alone, because a resource server rejects a bad token on any path
 * it covers, even one that permits all. Other endpoints get no authentication from this, but
 * Spring Security's firewall still runs on every request: it answers 400 to a path with `%25` or
 * a semicolon in it.
 *
 * CSRF protection is off for the same reasons as the cart's, and `/me` only reads.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher(CURRENT_CUSTOMER_PATH)
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .exceptionHandling { it.authenticationEntryPoint(tokenErrorEntryPoint()) }
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
     * a user ID.
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
     * Every 401. No token is `SIGN_IN_REQUIRED`. A token that fails verification is
     * `INVALID_TOKEN`, or `TOKEN_EXPIRED` when it has expired, which the customer app's API
     * client answers by refreshing and retrying.
     */
    private fun tokenErrorEntryPoint() = AuthenticationEntryPoint { request, response, exception: AuthenticationException ->
        val (message, code) = when {
            accessTokenCookieResolver().resolve(request) == null -> "Sign in to continue" to "SIGN_IN_REQUIRED"
            exception.isExpiry() -> "TOKEN_EXPIRED" to "TOKEN_EXPIRED"
            else -> "INVALID_TOKEN" to "INVALID_TOKEN"
        }
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write("""{"error":"$message","code":"$code"}""")
    }

    private fun Throwable.isExpiry(): Boolean =
        generateSequence(this) { it.cause }.any { it.message?.contains("expired", ignoreCase = true) == true }

    companion object {
        const val CURRENT_CUSTOMER_PATH = "/api/v1/customers/me"
        const val ACCESS_TOKEN_COOKIE = "access_token"
        const val TOKEN_USE_CLAIM = "token_use"
        const val TOKEN_USE_ACCESS = "access"
    }
}
