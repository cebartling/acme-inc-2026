package com.acme.identity.domain

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserTest {

    @Test
    fun `users with same ID should be equal`() {
        val id = UUID.randomUUID()
        val now = Instant.now()

        val user1 = createUser(id, now)
        val user2 = createUser(id, now)

        assertEquals(user1, user2)
    }

    @Test
    fun `users with different IDs should not be equal`() {
        val now = Instant.now()

        val user1 = createUser(UUID.randomUUID(), now)
        val user2 = createUser(UUID.randomUUID(), now)

        assertNotEquals(user1, user2)
    }

    @Test
    fun `getUserId should return correct UserId`() {
        val id = UUID.randomUUID()
        val user = createUser(id, Instant.now())

        assertEquals(UserId(id), user.getUserId())
    }

    @Test
    fun `user should have correct default status`() {
        val user = createUser(UUID.randomUUID(), Instant.now())

        assertEquals(UserStatus.PENDING_VERIFICATION, user.status)
    }

    @Test
    fun `lock should set status to LOCKED`() {
        val user = createActiveUser()

        user.lock(Duration.ofMinutes(30))

        assertEquals(UserStatus.LOCKED, user.status)
    }

    @Test
    fun `lock should set lockedUntil to future time`() {
        val user = createActiveUser()
        val beforeLock = Instant.now()

        user.lock(Duration.ofMinutes(30))

        assertNotNull(user.lockedUntil)
        assertTrue(user.lockedUntil!!.isAfter(beforeLock))
    }

    @Test
    fun `unlock should set status to ACTIVE`() {
        val user = createActiveUser()
        user.lock(Duration.ofMinutes(30))

        user.unlock()

        assertEquals(UserStatus.ACTIVE, user.status)
    }

    @Test
    fun `unlock should clear lockedUntil`() {
        val user = createActiveUser()
        user.lock(Duration.ofMinutes(30))

        user.unlock()

        assertNull(user.lockedUntil)
    }

    @Test
    fun `unlock should reset failed attempts`() {
        val user = createActiveUser()
        user.incrementFailedAttempts()
        user.incrementFailedAttempts()
        user.lock(Duration.ofMinutes(30))

        user.unlock()

        assertEquals(0, user.failedAttempts)
    }

    @Test
    fun `isLocked should return true when status is LOCKED and lockedUntil is in future`() {
        val user = createActiveUser()
        user.lock(Duration.ofMinutes(30))

        assertTrue(user.isLocked())
    }

    @Test
    fun `isLocked should return false when lockedUntil is not set`() {
        val user = createActiveUser()

        assertFalse(user.isLocked())
    }

    @Test
    fun `isLocked should return false after unlock`() {
        val user = createActiveUser()
        user.lock(Duration.ofMinutes(30))
        user.unlock()

        assertFalse(user.isLocked())
    }

    private fun createActiveUser(): User {
        val user = createUser(UUID.randomUUID(), Instant.now())
        user.status = UserStatus.ACTIVE
        return user
    }

    private fun createUser(id: UUID, tosAcceptedAt: Instant): User {
        return User(
            id = id,
            email = "test@example.com",
            passwordHash = "hash",
            firstName = "Test",
            lastName = "User",
            tosAcceptedAt = tosAcceptedAt,
            registrationSource = RegistrationSource.WEB
        )
    }
}
