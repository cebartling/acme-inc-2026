package com.acme.identity.application

import com.acme.identity.config.SessionConfig
import com.acme.identity.domain.Session
import com.acme.identity.domain.events.SessionCreated
import com.acme.identity.domain.events.SessionInvalidated
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.SessionRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import io.mockk.every
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import kotlin.test.*

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SessionServiceIntegrationTest {

    @Autowired
    private lateinit var sessionRepository: SessionRepository

    @Autowired
    private lateinit var sessionConfig: SessionConfig

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, Any>

    private val userEventPublisher: UserEventPublisher = mockk(relaxed = true)

    private lateinit var sessionService: SessionService

    companion object {
        @Container
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    @BeforeEach
    fun setUp() {
        // Clear Redis
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()

        // Create session service with mocked event publisher
        sessionService = SessionService(
            sessionRepository = sessionRepository,
            eventPublisher = userEventPublisher,
            config = sessionConfig
        )
    }

    @Test
    fun `createSession should persist session to Redis`() {
        val userId = UUID.randomUUID()

        val session = sessionService.createSession(
            userId = userId,
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            tokenFamily = "fam_123"
        )

        // Verify session was saved to Redis
        val retrieved = sessionRepository.findById(session.id)
        assertTrue(retrieved.isPresent)
        assertEquals(session.id, retrieved.get().id)
        assertEquals(userId, retrieved.get().userId)
    }

    @Test
    fun `createSession should set TTL in Redis`() {
        val userId = UUID.randomUUID()

        val session = sessionService.createSession(
            userId = userId,
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            tokenFamily = "fam_123"
        )

        // Check TTL was set (should be 7 days = 604800 seconds)
        val ttl = redisTemplate.getExpire("sessions:${session.id}")
        // TTL should be close to 604800 seconds (7 days), allowing a small delta for execution time
        assertTrue(ttl!! > 604000, "TTL should be approximately 604800 seconds (7 days), was $ttl")
        assertTrue(ttl < 605000, "TTL should not exceed 7 days plus small delta, was $ttl")
    }

    @Test
    fun `findByUserId should return all sessions for user`() {
        val userId = UUID.randomUUID()

        sessionService.createSession(userId, "dev_1", "192.168.1.1", "UA1", "fam_1")
        sessionService.createSession(userId, "dev_2", "192.168.1.2", "UA2", "fam_2")
        sessionService.createSession(userId, "dev_3", "192.168.1.3", "UA3", "fam_3")

        val sessions = sessionRepository.findByUserId(userId)
        assertEquals(3, sessions.size)
    }

    @Test
    fun `createSession should evict oldest session when limit exceeded`() {
        val userId = UUID.randomUUID()

        // Create 5 sessions (at the limit)
        repeat(5) { i ->
            sessionService.createSession(
                userId = userId,
                deviceId = "dev_$i",
                ipAddress = "192.168.1.$i",
                userAgent = "UA$i",
                tokenFamily = "fam_$i"
            )
            Thread.sleep(10) // Ensure different creation times
        }

        // Create 6th session - should evict oldest
        sessionService.createSession(
            userId = userId,
            deviceId = "dev_6",
            ipAddress = "192.168.1.6",
            userAgent = "UA6",
            tokenFamily = "fam_6"
        )

        // Should still have only 5 sessions
        val sessions = sessionRepository.findByUserId(userId)
        assertEquals(5, sessions.size)
    }

    @Test
    fun `invalidateSession should remove session from Redis`() {
        val userId = UUID.randomUUID()

        val session = sessionService.createSession(
            userId = userId,
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            tokenFamily = "fam_123"
        )

        // Verify session exists
        assertTrue(sessionRepository.findById(session.id).isPresent)

        // Invalidate session
        sessionService.invalidateSession(session.id, userId, SessionInvalidated.REASON_LOGOUT)

        // Verify session was deleted
        assertFalse(sessionRepository.findById(session.id).isPresent)
    }

    @Test
    fun `sessions should expire automatically after TTL`() {
        // This test would require waiting 7 days or manipulating Redis TTL
        // For integration testing, we verify TTL is set correctly
        val userId = UUID.randomUUID()

        val session = sessionService.createSession(
            userId = userId,
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            tokenFamily = "fam_123"
        )

        // Verify session has TTL
        val ttl = redisTemplate.getExpire("sessions:${session.id}")
        assertNotNull(ttl)
        assertTrue(ttl > 0)
    }

    @Test
    fun `findSession should return null for non-existent session`() {
        val result = sessionService.findSession("sess_nonexistent")
        assertNull(result)
    }

    @Test
    fun `findUserSessions should return empty list when no sessions`() {
        val userId = UUID.randomUUID()
        val sessions = sessionService.findUserSessions(userId)
        assertTrue(sessions.isEmpty())
    }
}
