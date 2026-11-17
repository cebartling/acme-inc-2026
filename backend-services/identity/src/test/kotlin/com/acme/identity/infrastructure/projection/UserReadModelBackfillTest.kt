package com.acme.identity.infrastructure.projection

import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.User
import com.acme.identity.infrastructure.messaging.OutboxWriter
import com.acme.identity.infrastructure.persistence.UserRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class UserReadModelBackfillTest {

    private lateinit var userRepository: UserRepository
    private lateinit var outboxWriter: OutboxWriter
    private lateinit var backfill: UserReadModelBackfill

    private val users = (1..5).map {
        User(
            id = UUID.randomUUID(),
            email = "backfill-$it@example.com",
            passwordHash = "hash",
            firstName = "Back",
            lastName = "Fill",
            tosAcceptedAt = Instant.parse("2026-11-16T11:00:00Z"),
            registrationSource = RegistrationSource.WEB
        )
    }

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        outboxWriter = mockk()
        backfill = UserReadModelBackfill(userRepository, outboxWriter, pageSize = 2)
        every { userRepository.findAll(any<Pageable>()) } answers {
            val page = firstArg<Pageable>()
            val from = page.pageNumber * page.pageSize
            PageImpl(users.drop(from).take(page.pageSize), page, users.size.toLong())
        }
        every { outboxWriter.writeSnapshot(any(), any(), any()) } just Runs
    }

    @Test
    fun `writes a snapshot for every user, across pages`() {
        val result = backfill.backfill()

        assertEquals(BackfillResult(total = 5, written = 5, failed = 0), result)
        users.forEach { verify(exactly = 1) { outboxWriter.writeSnapshot(it.id, "User", any()) } }
    }

    @Test
    fun `carries on past a user it cannot snapshot, and counts it`() {
        every { outboxWriter.writeSnapshot(users[1].id, any(), any()) } throws
            IllegalStateException("Cannot snapshot user ${users[1].id}: not found")

        val result = backfill.backfill()

        assertEquals(BackfillResult(total = 5, written = 4, failed = 1), result)
        verify(exactly = 1) { outboxWriter.writeSnapshot(users[4].id, "User", any()) }
    }

    @Test
    fun `does nothing when there are no users`() {
        every { userRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList(), PageRequest.of(0, 2), 0)

        assertEquals(BackfillResult(total = 0, written = 0, failed = 0), backfill.backfill())
        verify(exactly = 0) { outboxWriter.writeSnapshot(any(), any(), any()) }
    }
}
