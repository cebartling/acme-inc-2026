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

    private fun order(customerId: UUID = UUID.randomUUID(), idempotencyKey: String = "key-1") = Order(
        id = ids.generate(),
        orderNumber = orderNumbers.nextOrderNumber(LocalDate.of(2026, 10, 22)).value,
        customerId = customerId,
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

        val found = orders.findByCustomerIdAndIdempotencyKey(saved.customerId, "key-1")!!

        assertEquals(saved.id, found.id)
        inTransaction {
            val lines = orders.findById(saved.id).get().items
            assertEquals(1, lines.size)
            assertEquals(2, lines.single().quantity)
        }
    }

    @Test
    fun `the same idempotency key is refused for one customer but allowed for another`() {
        val customerId = UUID.randomUUID()
        orders.saveAndFlush(order(customerId))

        assertThrows<DataIntegrityViolationException> { orders.saveAndFlush(order(customerId)) }
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

    @Test
    fun `markCancelled records the cart's code on a pending order`() {
        val saved = orders.save(order())

        inTransaction { orders.markCancelled(saved.id, "CHECKOUT_SESSION_NOT_FOUND", Instant.now()) }

        val found = orders.findById(saved.id).get()
        assertEquals(OrderStatus.CANCELLED, found.status)
        assertEquals("CHECKOUT_SESSION_NOT_FOUND", found.cancelCode)
    }
}
