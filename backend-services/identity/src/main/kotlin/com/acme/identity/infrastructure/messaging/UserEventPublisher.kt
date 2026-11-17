package com.acme.identity.infrastructure.messaging

import com.acme.identity.domain.events.AccountLocked
import com.acme.identity.domain.events.AccountUnlocked
import com.acme.identity.domain.events.AuthenticationFailed
import com.acme.identity.domain.events.AuthenticationSucceeded
import com.acme.identity.domain.events.DeviceRemembered
import com.acme.identity.domain.events.DeviceRevoked
import com.acme.identity.domain.events.EmailVerified
import com.acme.identity.domain.events.MFAChallengeInitiated
import com.acme.identity.domain.events.MFAVerificationFailed
import com.acme.identity.domain.events.MFAVerificationSucceeded
import com.acme.identity.domain.events.PasswordChanged
import com.acme.identity.domain.events.PasswordResetRequested
import com.acme.identity.domain.events.ReactivationRequested
import com.acme.identity.domain.events.SessionCreated
import com.acme.identity.domain.events.SessionInvalidated
import com.acme.identity.domain.events.TokenReuseDetected
import com.acme.identity.domain.events.UserActivated
import com.acme.identity.domain.events.UserLoggedIn
import com.acme.identity.domain.events.UserRegistered
import org.springframework.stereotype.Component

/**
 * Records identity's domain events in the transactional outbox (PIN-548, ADR-0022).
 *
 * Debezium's outbox event router publishes each row to the event's topic, keyed by aggregate id,
 * so all events for a user stay in order within a partition. The topics, keys and JSON payloads
 * are the ones this class used to send to Kafka directly.
 *
 * Each method joins the caller's transaction. Session, device and token-reuse events describe
 * state in Redis and are written outside any transaction, so they are saved in their own and are
 * not atomic with the Redis change. A failed write throws: the event is never silently dropped.
 * The method names are the ones callers already use; "publish" now means "hand to the outbox".
 *
 * The events that change what the users read model holds (registration, email verification,
 * activation, lock and unlock) also write a snapshot of the user, so call them after the change
 * is saved.
 */
@Component
class UserEventPublisher(private val outboxWriter: OutboxWriter) {

    fun publish(event: UserRegistered) = outboxWriter.writeWithSnapshot(event, UserRegistered.TOPIC)

    fun publishEmailVerified(event: EmailVerified) = outboxWriter.writeWithSnapshot(event, EmailVerified.TOPIC)

    fun publishUserActivated(event: UserActivated) = outboxWriter.writeWithSnapshot(event, UserActivated.TOPIC)

    fun publishAuthenticationFailed(event: AuthenticationFailed) = outboxWriter.write(event, AuthenticationFailed.TOPIC)

    fun publishAuthenticationSucceeded(event: AuthenticationSucceeded) = outboxWriter.write(event, AuthenticationSucceeded.TOPIC)

    fun publishAccountLocked(event: AccountLocked) = outboxWriter.writeWithSnapshot(event, AccountLocked.TOPIC)

    fun publishAccountUnlocked(event: AccountUnlocked) = outboxWriter.writeWithSnapshot(event, AccountUnlocked.TOPIC)

    fun publishMFAChallengeInitiated(event: MFAChallengeInitiated) = outboxWriter.write(event, MFAChallengeInitiated.TOPIC)

    fun publishMFAVerificationSucceeded(event: MFAVerificationSucceeded) = outboxWriter.write(event, MFAVerificationSucceeded.TOPIC)

    fun publishMFAVerificationFailed(event: MFAVerificationFailed) = outboxWriter.write(event, MFAVerificationFailed.TOPIC)

    fun publish(event: SessionCreated) = outboxWriter.write(event, SessionCreated.TOPIC)

    fun publish(event: UserLoggedIn) = outboxWriter.write(event, UserLoggedIn.TOPIC)

    fun publish(event: SessionInvalidated) = outboxWriter.write(event, SessionInvalidated.TOPIC)

    fun publishDeviceRemembered(event: DeviceRemembered) = outboxWriter.write(event, DeviceRemembered.TOPIC)

    fun publishReactivationRequested(event: ReactivationRequested) = outboxWriter.write(event, ReactivationRequested.TOPIC)

    fun publishTokenReuseDetected(event: TokenReuseDetected) = outboxWriter.write(event, TokenReuseDetected.TOPIC)

    fun publishDeviceRevoked(event: DeviceRevoked) = outboxWriter.write(event, DeviceRevoked.TOPIC)

    fun publishPasswordResetRequested(event: PasswordResetRequested) = outboxWriter.write(event, PasswordResetRequested.TOPIC)

    fun publishPasswordChanged(event: PasswordChanged) = outboxWriter.write(event, PasswordChanged.TOPIC)
}
