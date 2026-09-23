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
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain

/**
 * Optional authentication for the cart API (US-0004-08).
 *
 * Every endpoint stays open to guests. When the browser sends identity's `access_token`
 * cookie, the token is verified (see [JwtDecoderConfig]) and the caller is that user; the
 * controller then acts on their user cart instead of the session's.
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
    }
}
