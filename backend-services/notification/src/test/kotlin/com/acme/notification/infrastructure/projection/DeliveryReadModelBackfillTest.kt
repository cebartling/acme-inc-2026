package com.acme.notification.infrastructure.projection

import com.acme.notification.domain.NotificationDelivery
import com.acme.notification.domain.NotificationType
import com.acme.notification.infrastructure.messaging.OutboxWriter
import com.acme.notification.infrastructure.persistence.NotificationDeliveryRepository
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
import java.util.UUID
import kotlin.test.assertEquals

class DeliveryReadModelBackfillTest {

    private lateinit var deliveryRepository: NotificationDeliveryRepository
    private lateinit var outboxWriter: OutboxWriter
    private lateinit var backfill: DeliveryReadModelBackfill

    private val deliveries = (1..5).map {
        NotificationDelivery(
            id = UUID.randomUUID(),
            notificationType = NotificationType.WELCOME,
            recipientId = UUID.randomUUID(),
            recipientEmail = "backfill-$it@example.com"
        )
    }

    @BeforeEach
    fun setUp() {
        deliveryRepository = mockk()
        outboxWriter = mockk()
        backfill = DeliveryReadModelBackfill(deliveryRepository, outboxWriter, pageSize = 2)
        every { deliveryRepository.findAll(any<Pageable>()) } answers {
            val page = firstArg<Pageable>()
            val from = page.pageNumber * page.pageSize
            PageImpl(deliveries.drop(from).take(page.pageSize), page, deliveries.size.toLong())
        }
        every { outboxWriter.writeSnapshot(any(), any()) } just Runs
    }

    @Test
    fun `writes a snapshot for every delivery, across pages`() {
        val result = backfill.backfill()

        assertEquals(BackfillResult(total = 5, written = 5, failed = 0), result)
        deliveries.forEach { verify(exactly = 1) { outboxWriter.writeSnapshot(it, any()) } }
    }

    @Test
    fun `carries on past a delivery it cannot snapshot, and counts it`() {
        every { outboxWriter.writeSnapshot(deliveries[1], any()) } throws IllegalStateException("outbox down")

        val result = backfill.backfill()

        assertEquals(BackfillResult(total = 5, written = 4, failed = 1), result)
        verify(exactly = 1) { outboxWriter.writeSnapshot(deliveries[4], any()) }
    }

    @Test
    fun `does nothing when there are no deliveries`() {
        every { deliveryRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList(), PageRequest.of(0, 2), 0)

        assertEquals(BackfillResult(total = 0, written = 0, failed = 0), backfill.backfill())
        verify(exactly = 0) { outboxWriter.writeSnapshot(any(), any()) }
    }
}
