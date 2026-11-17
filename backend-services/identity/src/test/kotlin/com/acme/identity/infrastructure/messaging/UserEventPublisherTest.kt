package com.acme.identity.infrastructure.messaging

import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.events.AccountLockReason
import com.acme.identity.domain.events.AccountLocked
import com.acme.identity.domain.events.AuthenticationFailed
import com.acme.identity.domain.events.AuthenticationFailureReason
import com.acme.identity.domain.events.DeviceRemembered
import com.acme.identity.domain.events.DomainEvent
import com.acme.identity.domain.events.MFAChallengeInitiated
import com.acme.identity.domain.events.SessionCreated
import com.acme.identity.domain.events.UserRegistered
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Identity's events go to the outbox, not straight to Kafka (PIN-548). One event per topic checks
 * that each `publish*` method writes its own event to its own topic.
 */
class UserEventPublisherTest {

    private val outboxWriter = mockk<OutboxWriter>()
    private val publisher = UserEventPublisher(outboxWriter)

    private val userId = UUID.randomUUID()
    private val later = Instant.parse("2026-11-16T12:30:00Z")

    private fun expectWrite(event: DomainEvent, topic: String) {
        every { outboxWriter.write(event, topic) } just Runs
    }

    @Test
    fun `UserRegistered goes to identity user events`() {
        val event = UserRegistered.create(
            userId, "new@example.com", "New", "User", later, false, RegistrationSource.WEB, "token"
        )
        expectWrite(event, "identity.user.events")

        publisher.publish(event)

        verify(exactly = 1) { outboxWriter.write(event, "identity.user.events") }
    }

    @Test
    fun `AccountLocked goes to identity account events`() {
        val event = AccountLocked.create(
            userId, "locked@example.com", AccountLockReason.EXCESSIVE_FAILED_ATTEMPTS, 5, later, "127.0.0.1", "agent"
        )
        expectWrite(event, "identity.account.events")

        publisher.publishAccountLocked(event)

        verify(exactly = 1) { outboxWriter.write(event, "identity.account.events") }
    }

    @Test
    fun `AuthenticationFailed goes to identity authentication events`() {
        val event = AuthenticationFailed.create(
            userId, "user@example.com", AuthenticationFailureReason.USER_NOT_FOUND, "127.0.0.1", "agent", 1
        )
        expectWrite(event, "identity.authentication.events")

        publisher.publishAuthenticationFailed(event)

        verify(exactly = 1) { outboxWriter.write(event, "identity.authentication.events") }
    }

    @Test
    fun `DeviceRemembered goes to identity device events`() {
        val event = DeviceRemembered.create(userId, "trust-1", "fingerprint", "agent", "127.0.0.1", later)
        expectWrite(event, "identity.device.events")

        publisher.publishDeviceRemembered(event)

        verify(exactly = 1) { outboxWriter.write(event, "identity.device.events") }
    }

    @Test
    fun `MFAChallengeInitiated goes to identity mfa events`() {
        val event = MFAChallengeInitiated.create(userId, "mfa-token", "TOTP", later)
        expectWrite(event, "identity.mfa.events")

        publisher.publishMFAChallengeInitiated(event)

        verify(exactly = 1) { outboxWriter.write(event, "identity.mfa.events") }
    }

    @Test
    fun `SessionCreated goes to identity session events`() {
        val event = SessionCreated.create("sess_${UUID.randomUUID()}", userId, "device-1", "127.0.0.1", "agent", later)
        expectWrite(event, "identity.session.events")

        publisher.publish(event)

        verify(exactly = 1) { outboxWriter.write(event, "identity.session.events") }
    }
}
