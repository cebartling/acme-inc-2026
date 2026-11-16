package com.acme.cart.infrastructure.projection

import com.acme.cart.domain.CartStatus
import com.acme.cart.infrastructure.messaging.OutboxWriter
import com.acme.cart.infrastructure.persistence.CartRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.assertEquals

class CartReadModelBackfillTest {

    private val cartRepository = mockk<CartRepository>()
    private val outboxWriter = mockk<OutboxWriter>()
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)
    private val backfill = CartReadModelBackfill(
        cartRepository, outboxWriter, TransactionTemplate(transactionManager), pageSize = 2
    )

    private val cartIds = (1..5).map { UUID.randomUUID() }

    @BeforeEach
    fun setUp() {
        every { cartRepository.findIdsByStatusIn(CartStatus.CURRENT, any<Pageable>()) } answers {
            val page = secondArg<Pageable>()
            val from = page.pageNumber * page.pageSize
            val content = cartIds.drop(from).take(page.pageSize)
            SliceImpl(content, page, from + content.size < cartIds.size)
        }
        every { outboxWriter.writeSnapshot(any()) } returns Unit
    }

    @Test
    fun `writes a snapshot for every current cart, across pages`() {
        val result = backfill.backfill()

        assertEquals(BackfillResult(written = 5, failed = 0), result)
        cartIds.forEach { verify(exactly = 1) { outboxWriter.writeSnapshot(it) } }
    }

    @Test
    fun `carries on past a cart it cannot snapshot, and counts it`() {
        every { outboxWriter.writeSnapshot(cartIds[1]) } throws IllegalStateException("boom")

        val result = backfill.backfill()

        assertEquals(BackfillResult(written = 4, failed = 1), result)
        verify(exactly = 1) { outboxWriter.writeSnapshot(cartIds[4]) }
    }

    @Test
    fun `does nothing when there are no current carts`() {
        every { cartRepository.findIdsByStatusIn(CartStatus.CURRENT, any<Pageable>()) } returns
            SliceImpl(emptyList(), PageRequest.of(0, 2), false)

        assertEquals(BackfillResult(written = 0, failed = 0), backfill.backfill())
    }
}
