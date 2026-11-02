package com.acme.order.application

import com.acme.order.infrastructure.persistence.OrderRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import java.util.UUID

class ListOrdersUseCaseTest {

    private val orders = mockk<OrderRepository>()
    private val useCase = ListOrdersUseCase(orders)
    private val userId = UUID.randomUUID()

    @Test
    fun `lists the caller's orders, turning the 1-based page into a 0-based one`() {
        every { orders.findSummaries(any(), any()) } returns Page.empty()

        useCase.execute(userId, page = 3, pageSize = 10)

        verify { orders.findSummaries(userId, PageRequest.of(2, 10)) }
    }

    @Test
    fun `a page or page size below one fails fast`() {
        assertThrows<IllegalArgumentException> { useCase.execute(userId, page = 0, pageSize = 10) }
        assertThrows<IllegalArgumentException> { useCase.execute(userId, page = 1, pageSize = 0) }
    }
}
