package com.acme.identity.application

import com.acme.identity.config.SessionConfig
import com.acme.identity.domain.Session
import com.acme.identity.domain.events.SessionCreated
import com.acme.identity.domain.events.SessionInvalidated
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.SessionRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.*

class SessionServiceTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var userEventPublisher: UserEventPublisher
    private lateinit var sessionConfig: SessionConfig
    private lateinit var sessionService: SessionService

    private val testUserId = UUID.randomUUID()
    private val testDeviceId = "dev_abc123"
    private val testIpAddress = "192.168.1.100"
    private val testUserAgent = "Mozilla/5.0..."
    private val testTokenFamily = "fam_${UUID.randomUUID()}"

    @BeforeEach
    fun setUp() {
        sessionRepository = mockk()
        userEventPublisher = mockk(relaxed = true)
        sessionConfig = SessionConfig(
            maxPerUser = 5,
            ttlDays = 7
        )
        sessionService = SessionService(
            sessionRepository = sessionRepository,
            eventPublisher = userEventPublisher,
            config = sessionConfig
        )
    }

    @Test
    fun `createSession should save session and publish event`() {
        // Given
        val sessionSlot = slot<Session>()
        val eventSlot = slot<SessionCreated>()

        every { sessionRepository.findByUserId(testUserId) } returns emptyList()
        every { sessionRepository.save(capture(sessionSlot)) } answers { firstArg() }
        every { userEventPublisher.publish(capture(eventSlot)) } returns mockk()

        // When
        val session = sessionService.createSession(
            userId = testUserId,
            deviceId = testDeviceId,
            ipAddress = testIpAddress,
            userAgent = testUserAgent,
            tokenFamily = testTokenFamily
        )

        // Then
        assertNotNull(session)
        assertTrue(session.id.startsWith("sess_"))
        assertEquals(testUserId, session.userId)
        assertEquals(testDeviceId, session.deviceId)
        assertEquals(testIpAddress, session.ipAddress)
        assertEquals(testUserAgent, session.userAgent)
        assertEquals(testTokenFamily, session.tokenFamily)
        assertEquals(604800L, session.ttl) // 7 days

        verify(exactly = 1) { sessionRepository.save(any()) }
        verify(exactly = 1) { userEventPublisher.publish(any<SessionCreated>()) }

        val publishedEvent = eventSlot.captured
        assertEquals(session.id, publishedEvent.payload.sessionId)
        assertEquals(testUserId, publishedEvent.payload.userId)
    }

    @Test
    fun `createSession should evict oldest session when limit exceeded`() {
        // Given
        val oldestSession = createTestSession("sess_${UUID.randomUUID()}", Instant.now().minusSeconds(86400))
        val existingSessions = listOf(
            oldestSession,
            createTestSession("sess_${UUID.randomUUID()}", Instant.now().minusSeconds(7200)),
            createTestSession("sess_${UUID.randomUUID()}", Instant.now().minusSeconds(3600)),
            createTestSession("sess_${UUID.randomUUID()}", Instant.now().minusSeconds(1800)),
            createTestSession("sess_${UUID.randomUUID()}", Instant.now().minusSeconds(900))
        )

        val invalidatedEventSlot = slot<SessionInvalidated>()

        every { sessionRepository.findByUserId(testUserId) } returns existingSessions
        every { sessionRepository.delete(oldestSession) } just Runs
        every { sessionRepository.save(any()) } answers { firstArg() }
        every { userEventPublisher.publish(capture(invalidatedEventSlot)) } answers { mockk() }

        // When
        sessionService.createSession(
            userId = testUserId,
            deviceId = testDeviceId,
            ipAddress = testIpAddress,
            userAgent = testUserAgent,
            tokenFamily = testTokenFamily
        )

        // Then
        verify(exactly = 1) { sessionRepository.delete(oldestSession) }
        verify(exactly = 1) { userEventPublisher.publish(any<SessionInvalidated>()) }

        val invalidatedEvent = invalidatedEventSlot.captured
        assertEquals(oldestSession.id, invalidatedEvent.payload.sessionId)
        assertEquals(SessionInvalidated.REASON_CONCURRENT_LIMIT, invalidatedEvent.payload.reason)
    }

    @Test
    fun `createSession should not evict when under limit`() {
        // Given
        val existingSessions = listOf(
            createTestSession("sess_1", Instant.now().minusSeconds(3600)),
            createTestSession("sess_2", Instant.now().minusSeconds(1800))
        )

        every { sessionRepository.findByUserId(testUserId) } returns existingSessions
        every { sessionRepository.save(any()) } answers { firstArg() }
        every { userEventPublisher.publish(any<SessionCreated>()) } returns mockk()

        // When
        sessionService.createSession(
            userId = testUserId,
            deviceId = testDeviceId,
            ipAddress = testIpAddress,
            userAgent = testUserAgent,
            tokenFamily = testTokenFamily
        )

        // Then
        verify(exactly = 0) { sessionRepository.delete(any()) }
        verify(exactly = 0) { userEventPublisher.publish(any<SessionInvalidated>()) }
    }

    @Test
    fun `invalidateSession should delete session and publish event`() {
        // Given
        val sessionId = "sess_${UUID.randomUUID()}"
        val reason = SessionInvalidated.REASON_LOGOUT
        val eventSlot = slot<SessionInvalidated>()

        every { sessionRepository.deleteById(sessionId) } just Runs
        every { userEventPublisher.publish(capture(eventSlot)) } answers { mockk() }

        // When
        sessionService.invalidateSession(sessionId, testUserId, reason)

        // Then
        verify(exactly = 1) { sessionRepository.deleteById(sessionId) }
        verify(exactly = 1) { userEventPublisher.publish(any<SessionInvalidated>()) }

        val event = eventSlot.captured
        assertEquals(sessionId, event.payload.sessionId)
        assertEquals(testUserId, event.payload.userId)
        assertEquals(reason, event.payload.reason)
    }

    @Test
    fun `findSession should return session when exists`() {
        // Given
        val sessionId = "sess_123"
        val session = createTestSession(sessionId, Instant.now())

        every { sessionRepository.findById(sessionId) } returns Optional.of(session)

        // When
        val result = sessionService.findSession(sessionId)

        // Then
        assertNotNull(result)
        assertEquals(sessionId, result.id)
    }

    @Test
    fun `findSession should return null when not exists`() {
        // Given
        val sessionId = "sess_nonexistent"

        every { sessionRepository.findById(sessionId) } returns Optional.empty()

        // When
        val result = sessionService.findSession(sessionId)

        // Then
        assertNull(result)
    }

    @Test
    fun `findUserSessions should return all sessions for user`() {
        // Given
        val sessions = listOf(
            createTestSession("sess_1", Instant.now()),
            createTestSession("sess_2", Instant.now())
        )

        every { sessionRepository.findByUserId(testUserId) } returns sessions

        // When
        val result = sessionService.findUserSessions(testUserId)

        // Then
        assertEquals(2, result.size)
    }

    @Test
    fun `createSession should set correct expiration time`() {
        // Given
        val sessionSlot = slot<Session>()

        every { sessionRepository.findByUserId(testUserId) } returns emptyList()
        every { sessionRepository.save(capture(sessionSlot)) } answers { firstArg() }
        every { userEventPublisher.publish(any<SessionCreated>()) } returns mockk()

        // When
        val beforeCreate = Instant.now()
        sessionService.createSession(
            userId = testUserId,
            deviceId = testDeviceId,
            ipAddress = testIpAddress,
            userAgent = testUserAgent,
            tokenFamily = testTokenFamily
        )
        val afterCreate = Instant.now()

        // Then
        val session = sessionSlot.captured
        val expectedExpiry = beforeCreate.plusSeconds(604800)

        // Allow 1 second tolerance for test execution time
        assertTrue(session.expiresAt.isAfter(expectedExpiry.minusSeconds(1)))
        assertTrue(session.expiresAt.isBefore(afterCreate.plusSeconds(604800).plusSeconds(1)))
    }

    private fun createTestSession(id: String, createdAt: Instant): Session {
        return Session(
            id = id,
            userId = testUserId,
            deviceId = testDeviceId,
            ipAddress = testIpAddress,
            userAgent = testUserAgent,
            tokenFamily = testTokenFamily,
            createdAt = createdAt,
            expiresAt = createdAt.plusSeconds(604800),
            ttl = 604800
        )
    }
}
