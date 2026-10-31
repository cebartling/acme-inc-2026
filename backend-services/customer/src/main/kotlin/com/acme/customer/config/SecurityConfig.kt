package com.acme.customer.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
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
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.security.web.firewall.HttpFirewall
import org.springframework.security.web.firewall.StrictHttpFirewall
import java.util.UUID

/**
 * Who may call `/api/v1/customers/`. Everything under it needs identity's `access_token` cookie,
 * verified as the cart and order services do (ADR-0043): Spring Boot's resource server, configured
 * under `spring.security.oauth2.resourceserver.jwt`, plus [accessTokenValidator]. The controllers
 * take the user from the verified token and check that they own the customer.
 *
 * The exception is the lookups that return a whole customer by an ID, number or email. No page
 * uses them, so they take the [ServiceCredentials] secret instead: the notification service reads
 * customers by ID.
 *
 * Other paths, such as actuator and the `test` profile's helpers, stay outside this chain.
 *
 * CSRF protection is off for the same reasons as the cart's: a JSON API, state-changing requests
 * need a JSON content type, and the `access_token` cookie is SameSite=Strict.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(private val serviceCredentials: ServiceCredentials) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("$CUSTOMERS_PATH/**")
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(CURRENT_CUSTOMER_PATH).authenticated()
                it.requestMatchers(
                    HttpMethod.GET,
                    "$CUSTOMERS_PATH/*",
                    "$CUSTOMERS_PATH/by-user/*",
                    "$CUSTOMERS_PATH/by-number/*",
                    "$CUSTOMERS_PATH/by-email/*"
                ).access(AuthorizationManager<RequestAuthorizationContext> { _, context ->
                    AuthorizationDecision(
                        serviceCredentials.isService(context.request.getHeader(ServiceCredentials.HEADER))
                    )
                })
                it.anyRequest().authenticated()
            }
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
     * Spring Security's firewall runs on every request, and by default answers 400 to an encoded
     * percent sign, which `by-email` needs: `%` is legal in an email address. Nothing here decodes
     * a path twice, which is what that default guards against.
     */
    @Bean
    fun httpFirewall(): HttpFirewall = StrictHttpFirewall().apply { setAllowUrlEncodedPercent(true) }

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
        const val CUSTOMERS_PATH = "/api/v1/customers"
        const val CURRENT_CUSTOMER_PATH = "$CUSTOMERS_PATH/me"
        const val ACCESS_TOKEN_COOKIE = "access_token"
        const val TOKEN_USE_CLAIM = "token_use"
        const val TOKEN_USE_ACCESS = "access"
    }
}
