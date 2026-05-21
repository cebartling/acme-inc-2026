package com.acme.identity.infrastructure.ratelimit

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SigninRateLimiterTest {

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun instant(): Instant = instant
        fun advance(seconds: Long) {
            instant = instant.plusSeconds(seconds)
        }
    }

    private lateinit var clock: MutableClock

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("acme_identity_test")
            .withUsername("test")
            .withPassword("test")

        @Container
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    @BeforeEach
    fun setUp() {
        redisTemplate.connectionFactory?.connection?.use { conn ->
            conn.serverCommands().flushAll()
        }
        clock = MutableClock(Instant.parse("2026-05-20T12:00:00Z"))
    }

    private fun newLimiter(
        enabled: Boolean = true,
        ipLimit: Int = 10,
        emailLimit: Int = 5,
        windowSeconds: Long = 60,
        template: StringRedisTemplate = redisTemplate
    ) = SigninRateLimiter(
        redisTemplate = template,
        enabled = enabled,
        ipLimit = ipLimit,
        ipWindowSeconds = windowSeconds,
        emailLimit = emailLimit,
        emailWindowSeconds = windowSeconds,
        clock = clock
    )

    @Test
    fun `allows the first 10 requests from the same IP and limits the 11th`() {
        val limiter = newLimiter()

        repeat(10) { i ->
            val result = limiter.checkLimit("1.2.3.4", "user$i@example.com")
            assertIs<SigninRateLimitResult.Allowed>(result, "request ${i + 1} should be allowed")
        }

        val eleventh = limiter.checkLimit("1.2.3.4", "user11@example.com")
        val limited = assertIs<SigninRateLimitResult.Limited>(eleventh)
        assertEquals(SigninRateLimitResult.Scope.IP, limited.scope)
        assertEquals(10, limited.limit)
        assertTrue(limited.retryAfterSeconds in 1..60)
    }

    @Test
    fun `allows the first 5 requests for the same email from different IPs and limits the 6th`() {
        val limiter = newLimiter()

        repeat(5) { i ->
            val result = limiter.checkLimit("10.0.0.$i", "victim@example.com")
            assertIs<SigninRateLimitResult.Allowed>(result, "request ${i + 1} should be allowed")
        }

        val sixth = limiter.checkLimit("10.0.0.99", "victim@example.com")
        val limited = assertIs<SigninRateLimitResult.Limited>(sixth)
        assertEquals(SigninRateLimitResult.Scope.EMAIL, limited.scope)
        assertEquals(5, limited.limit)
    }

    @Test
    fun `different IPs do not affect each other`() {
        val limiter = newLimiter()

        // Exhaust IP A's limit
        repeat(10) { limiter.checkLimit("1.1.1.1", "a$it@example.com") }
        val limitedA = limiter.checkLimit("1.1.1.1", "a@example.com")
        assertIs<SigninRateLimitResult.Limited>(limitedA)

        // IP B is unaffected
        val allowedB = limiter.checkLimit("2.2.2.2", "fresh@example.com")
        assertIs<SigninRateLimitResult.Allowed>(allowedB)
    }

    @Test
    fun `sliding window restores capacity after window elapses`() {
        val limiter = newLimiter(windowSeconds = 60)

        // Saturate IP at t=0
        repeat(10) { limiter.checkLimit("9.9.9.9", "u$it@example.com") }
        val blocked = limiter.checkLimit("9.9.9.9", "u@example.com")
        assertIs<SigninRateLimitResult.Limited>(blocked)

        // Advance past the window so all entries age out
        clock.advance(61)

        val afterWindow = limiter.checkLimit("9.9.9.9", "u@example.com")
        assertIs<SigninRateLimitResult.Allowed>(afterWindow)
    }

    @Test
    fun `Allowed result exposes remaining count consistent with limit`() {
        val limiter = newLimiter(ipLimit = 10, emailLimit = 5)

        val r1 = limiter.checkLimit("3.3.3.3", "one@example.com")
        val allowed = assertIs<SigninRateLimitResult.Allowed>(r1)
        // After the IP scope allows, the email scope is the more restrictive,
        // so the surfaced limit/remaining is the email scope's.
        assertEquals(5, allowed.limit)
        assertEquals(4, allowed.remaining)
    }

    @Test
    fun `retryAfter under burst reflects when capacity actually returns`() {
        val limiter = newLimiter(ipLimit = 10, windowSeconds = 60)

        // 10 requests at t=0 saturate the IP.
        repeat(10) { limiter.checkLimit("8.8.8.8", "u$it@example.com") }
        // 10 more at t=10s create a burst beyond the limit.
        clock.advance(10)
        repeat(10) { limiter.checkLimit("8.8.8.8", "burst$it@example.com") }

        // 21st request: count=21, rank (count-limit-1)=10 — the first burst entry
        // at t=10s. Its expiry at t=70s (60s window) is what truly frees capacity,
        // so Retry-After should be ~60s, NOT ~50s (the oldest at t=0 expires at t=60).
        val limited = limiter.checkLimit("8.8.8.8", "extra@example.com")
        val result = assertIs<SigninRateLimitResult.Limited>(limited)
        assertTrue(
            result.retryAfterSeconds in 55..60,
            "expected retryAfter ~60s for burst, got ${result.retryAfterSeconds}"
        )
    }

    @Test
    fun `disabled limiter does not touch redis and returns Allowed`() {
        val template = mockk<StringRedisTemplate>()
        val limiter = newLimiter(enabled = false, template = template)

        val result = limiter.checkLimit("1.2.3.4", "anyone@example.com")
        assertIs<SigninRateLimitResult.Allowed>(result)
        verify(exactly = 0) { template.opsForZSet() }
    }

    @Test
    fun `redis failure fails open and returns Allowed`() {
        val template = mockk<StringRedisTemplate>()
        every { template.opsForZSet() } throws RuntimeException("redis down")

        val limiter = newLimiter(template = template)

        val result = limiter.checkLimit("1.2.3.4", "anyone@example.com")
        assertIs<SigninRateLimitResult.Allowed>(result)
    }
}
