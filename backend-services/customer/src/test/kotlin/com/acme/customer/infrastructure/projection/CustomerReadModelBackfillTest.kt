package com.acme.customer.infrastructure.projection

import com.acme.customer.infrastructure.messaging.OutboxWriter
import com.acme.customer.infrastructure.persistence.CustomerRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.util.UUID
import kotlin.test.assertEquals

class CustomerReadModelBackfillTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var outboxWriter: OutboxWriter
    private lateinit var backfill: CustomerReadModelBackfill

    private val customers = (1..5).map { SnapshotFixtures.customer() }

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        outboxWriter = mockk()
        backfill = CustomerReadModelBackfill(customerRepository, outboxWriter, pageSize = 2)
        every { customerRepository.findAll(any<Pageable>()) } answers {
            val page = firstArg<Pageable>()
            val from = page.pageNumber * page.pageSize
            PageImpl(customers.drop(from).take(page.pageSize), page, customers.size.toLong())
        }
        every { outboxWriter.writeSnapshot(any(), any(), any()) } answers { UUID.randomUUID() }
    }

    @Test
    fun `writes a snapshot for every customer, across pages`() {
        val result = backfill.backfill()

        assertEquals(BackfillResult(total = 5, written = 5, failed = 0), result)
        customers.forEach { verify(exactly = 1) { outboxWriter.writeSnapshot(it.id, "Customer", any()) } }
    }

    @Test
    fun `carries on past a customer it cannot snapshot, and counts it`() {
        every { outboxWriter.writeSnapshot(customers[1].id, any(), any()) } throws
            IllegalStateException("Cannot snapshot customer ${customers[1].id}: no preferences")

        val result = backfill.backfill()

        assertEquals(BackfillResult(total = 5, written = 4, failed = 1), result)
        verify(exactly = 1) { outboxWriter.writeSnapshot(customers[4].id, "Customer", any()) }
    }

    @Test
    fun `does nothing when there are no customers`() {
        every { customerRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList(), PageRequest.of(0, 2), 0)

        assertEquals(BackfillResult(total = 0, written = 0, failed = 0), backfill.backfill())
    }
}
