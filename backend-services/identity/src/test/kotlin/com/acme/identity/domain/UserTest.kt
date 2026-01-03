package com.acme.identity.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
