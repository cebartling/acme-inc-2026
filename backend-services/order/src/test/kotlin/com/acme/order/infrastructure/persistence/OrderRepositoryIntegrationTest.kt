package com.acme.order.infrastructure.persistence

import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Orders against real Postgres (PIN-366): the V3 schema, the per-day order number
 * sequence, idempotency-key uniqueness, and the conditional status updates.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(OrderNumberSequenceRepository::class, OrderIdGenerator::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderRepositoryIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("acme_orders_test")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    @Autowired
    private lateinit var orders: OrderRepository

    @Autowired
    private lateinit var orderNumbers: OrderNumberSequenceRepository

    @Autowired
    private lateinit var ids: OrderIdGenerator

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @AfterEach
    fun cleanUp() {
        orders.deleteAll()
    }

    private fun order(userId: UUID = UUID.randomUUID(), idempotencyKey: String = "key-1") = Order(
        id = ids.generate(),
        orderNumber = orderNumbers.nextOrderNumber(LocalDate.of(2026, 10, 22)).value,
        userId = userId,
        checkoutSessionId = UUID.randomUUID(),
        cartId = UUID.randomUUID(),
        idempotencyKey = idempotencyKey,
        status = OrderStatus.PENDING,
        subtotal = BigDecimal("139.98"),
        currency = "USD",
        createdAt = Instant.parse("2026-10-22T12:00:00Z")
    ).apply {
        addItem(ids.generate(), UUID.randomUUID(), 2, BigDecimal("69.99"), BigDecimal("139.98"), """{"name":"Mouse"}""")
    }

    private fun inTransaction(block: () -> Unit) = TransactionTemplate(transactionManager).executeWithoutResult { block() }

    @Test
    fun `order numbers count up per day and restart on a new day`() {
        val day = LocalDate.of(2031, 1, 2)
        val first = orderNumbers.nextOrderNumber(day).value
        val second = orderNumbers.nextOrderNumber(day).value
        val nextDay = orderNumbers.nextOrderNumber(day.plusDays(1)).value

        assertEquals("ACME-20310102-000001", first)
        assertEquals("ACME-20310102-000002", second)
        assertEquals("ACME-20310103-000001", nextDay)
    }

    @Test
    fun `an order is saved with its lines and found by customer and idempotency key`() {
        val saved = order()
        orders.save(saved)

        val found = orders.findByUserIdAndIdempotencyKey(saved.userId, "key-1")!!

        assertEquals(saved.id, found.id)
        inTransaction {
            val lines = orders.findById(saved.id).get().items
            assertEquals(1, lines.size)
            assertEquals(2, lines.single().quantity)
        }
    }

    @Test
    fun `the same idempotency key is refused for one customer but allowed for another`() {
        val userId = UUID.randomUUID()
        orders.saveAndFlush(order(userId))

        assertThrows<DataIntegrityViolationException> { orders.saveAndFlush(order(userId)) }
        orders.saveAndFlush(order(UUID.randomUUID()))
    }

    @Test
    fun `markSubmitted moves a pending order once, and markCancelled then changes nothing`() {
        val saved = orders.save(order())
        val now = Instant.parse("2026-10-22T12:01:00Z")

        var first = 0
        var second = 0
        var cancelled = 0
        inTransaction { first = orders.markSubmitted(saved.id, now) }
        inTransaction { second = orders.markSubmitted(saved.id, now) }
        inTransaction { cancelled = orders.markCancelled(saved.id, "CART_ALREADY_CONVERTED", now) }

        assertEquals(1, first)
        assertEquals(0, second)
        assertEquals(0, cancelled)
        assertEquals(OrderStatus.SUBMITTED, orders.findById(saved.id).get().status)
    }

    private fun submitted(userId: UUID, createdAt: Instant, vararg quantities: Int, status: OrderStatus = OrderStatus.SUBMITTED) =
        orders.save(
            Order(
                id = ids.generate(),
                orderNumber = orderNumbers.nextOrderNumber(LocalDate.of(2026, 10, 22)).value,
                userId = userId,
                checkoutSessionId = UUID.randomUUID(),
                cartId = UUID.randomUUID(),
                idempotencyKey = UUID.randomUUID().toString(),
                status = status,
                subtotal = BigDecimal("10.00"),
                currency = "USD",
                createdAt = createdAt
            ).apply {
                quantities.forEach { addItem(ids.generate(), UUID.randomUUID(), it, BigDecimal("1.00"), BigDecimal(it), "{}") }
            }
        )

    @Test
    fun `findSummaries lists the user's orders in a status, newest first, with unit counts and paging`() {
        val userId = UUID.randomUUID()
        val base = Instant.parse("2026-10-22T12:00:00Z")
        val oldest = submitted(userId, base, 1)
        val middle = submitted(userId, base.plusSeconds(60), 2, 3)
        val newest = submitted(userId, base.plusSeconds(120), 4)
        submitted(userId, base.plusSeconds(180), 1, status = OrderStatus.PENDING)
        submitted(userId, base.plusSeconds(240), 1, status = OrderStatus.CANCELLED)
        submitted(UUID.randomUUID(), base.plusSeconds(300), 1)

        val first = orders.findSummaries(userId, OrderStatus.SUBMITTED, PageRequest.of(0, 2))
        val second = orders.findSummaries(userId, OrderStatus.SUBMITTED, PageRequest.of(1, 2))

        assertEquals(listOf(newest.id, middle.id), first.content.map { it.id })
        assertEquals(listOf(4L, 5L), first.content.map { it.itemCount })
        assertEquals(3, first.totalElements)
        assertEquals(2, first.totalPages)
        assertEquals(listOf(oldest.id), second.content.map { it.id })
    }

    @Test
    fun `findByIdAndUserIdAndStatus finds only the owner's order in that status`() {
        val userId = UUID.randomUUID()
        val saved = submitted(userId, Instant.parse("2026-10-22T12:00:00Z"), 2)

        assertEquals(saved.id, orders.findByIdAndUserIdAndStatus(saved.id, userId, OrderStatus.SUBMITTED)?.id)
        assertEquals(null, orders.findByIdAndUserIdAndStatus(saved.id, UUID.randomUUID(), OrderStatus.SUBMITTED))
        assertEquals(null, orders.findByIdAndUserIdAndStatus(saved.id, userId, OrderStatus.PENDING))
    }

    @Test
    fun `markCancelled records the cart's code on a pending order`() {
        val saved = orders.save(order())

        inTransaction { orders.markCancelled(saved.id, "CHECKOUT_SESSION_NOT_FOUND", Instant.now()) }

        val found = orders.findById(saved.id).get()
        assertEquals(OrderStatus.CANCELLED, found.status)
        assertEquals("CHECKOUT_SESSION_NOT_FOUND", found.cancelCode)
    }
}
