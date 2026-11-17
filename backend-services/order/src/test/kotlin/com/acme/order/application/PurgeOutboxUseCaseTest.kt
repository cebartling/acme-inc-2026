package com.acme.order.application

import com.acme.order.infrastructure.persistence.OutboxRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant

class PurgeOutboxUseCaseTest {

    private val outbox = mockk<OutboxRepository>()
    private val transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true))
    private val meterRegistry = SimpleMeterRegistry()
    private val now = Instant.parse("2026-11-16T12:00:00Z")

    @Test
    fun `it deletes the rows written before now minus the max age and returns the count`() {
        every { outbox.deleteCreatedBefore(any()) } returns 3

        val purged = PurgeOutboxUseCase(outbox, transactionTemplate, meterRegistry, Duration.ofDays(7)).execute(now)

        assertEquals(3, purged)
        assertEquals(3.0, meterRegistry.counter("outbox_rows_purged_total").count())
        verify { outbox.deleteCreatedBefore(Instant.parse("2026-11-09T12:00:00Z")) }
    }

    @Test
    fun `a max age that is not positive fails at construction`() {
        val error = assertThrows<IllegalArgumentException> {
            PurgeOutboxUseCase(outbox, transactionTemplate, meterRegistry, Duration.ZERO)
        }
        assertEquals("acme.order.outbox.retention.max-age must be positive, was PT0S", error.message)
    }
}
