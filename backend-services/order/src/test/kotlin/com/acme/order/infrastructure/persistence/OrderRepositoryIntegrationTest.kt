package com.acme.order.infrastructure.persistence

import com.acme.order.domain.CancelledBy
import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import org.flywaydb.core.Flyway
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
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

    private fun saveOrder(userId: UUID, createdAt: Instant, vararg quantities: Int, status: OrderStatus = OrderStatus.SUBMITTED) =
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
    fun `findSummaries lists the user's visible orders, newest first, with unit counts and paging`() {
        val userId = UUID.randomUUID()
        val base = Instant.parse("2026-10-22T12:00:00Z")
        val oldest = saveOrder(userId, base, 1)
        val middle = saveOrder(userId, base.plusSeconds(60), 2, 3)
        val newest = saveOrder(userId, base.plusSeconds(120), 4)
        saveOrder(userId, base.plusSeconds(180), 1, status = OrderStatus.PENDING)
        saveOrder(userId, base.plusSeconds(240), 1, status = OrderStatus.CANCELLED)
        saveOrder(UUID.randomUUID(), base.plusSeconds(300), 1)

        val first = orders.findSummaries(userId, PageRequest.of(0, 2))
        val second = orders.findSummaries(userId, PageRequest.of(1, 2))

        assertEquals(listOf(newest.id, middle.id), first.content.map { it.id })
        assertEquals(listOf(4L, 5L), first.content.map { it.itemCount })
        assertEquals(3, first.totalElements)
        assertEquals(2, first.totalPages)
        assertEquals(listOf(oldest.id), second.content.map { it.id })
    }

    @Test
    fun `findVisible finds only the owner's submitted order`() {
        val userId = UUID.randomUUID()
        val saved = saveOrder(userId, Instant.parse("2026-10-22T12:00:00Z"), 2)
        val pending = saveOrder(userId, Instant.parse("2026-10-22T12:00:00Z"), 2, status = OrderStatus.PENDING)

        assertEquals(saved.id, orders.findVisible(saved.id, userId)?.id)
        assertEquals(null, orders.findVisible(saved.id, UUID.randomUUID()))
        assertEquals(null, orders.findVisible(pending.id, userId))
    }

    @Test
    fun `a customer's cancelled order stays visible, and a failed placement doesn't`() {
        val userId = UUID.randomUUID()
        val base = Instant.parse("2026-10-22T12:00:00Z")
        val cancelled = saveOrder(userId, base, 2)
        val failed = saveOrder(userId, base.plusSeconds(60), 1, status = OrderStatus.PENDING)

        inTransaction { orders.markCancelledByCustomer(cancelled.id, userId, "Changed my mind", Instant.now()) }
        inTransaction { orders.markCancelled(failed.id, "CART_NOT_FOUND", Instant.now()) }

        assertEquals(OrderStatus.CANCELLED, orders.findVisible(cancelled.id, userId)?.status)
        assertEquals(null, orders.findVisible(failed.id, userId))
        val listed = orders.findSummaries(userId, PageRequest.of(0, 10))
        assertEquals(listOf(cancelled.id), listed.content.map { it.id })
        assertEquals(1, listed.totalElements)
    }

    @Test
    fun `markCancelled records the cart's code on a pending order, as a placement failure`() {
        val saved = orders.save(order())

        inTransaction { orders.markCancelled(saved.id, "CHECKOUT_SESSION_NOT_FOUND", Instant.now()) }

        val found = orders.findById(saved.id).get()
        assertEquals(OrderStatus.CANCELLED, found.status)
        assertEquals("CHECKOUT_SESSION_NOT_FOUND", found.cancelCode)
        assertEquals(CancelledBy.PLACEMENT, found.cancelledBy)
    }

    @Test
    fun `markCancelledByCustomer records who and why, and changes nothing a second time`() {
        val userId = UUID.randomUUID()
        val saved = saveOrder(userId, Instant.parse("2026-10-22T12:00:00Z"), 1)

        var first = 0
        var second = 0
        inTransaction { first = orders.markCancelledByCustomer(saved.id, userId, "Changed my mind", Instant.now()) }
        inTransaction { second = orders.markCancelledByCustomer(saved.id, userId, "Another reason", Instant.now()) }

        assertEquals(1, first)
        assertEquals(0, second)
        val found = orders.findById(saved.id).get()
        assertEquals(OrderStatus.CANCELLED, found.status)
        assertEquals(CancelledBy.CUSTOMER, found.cancelledBy)
        assertEquals("Changed my mind", found.cancelReason)
        assertEquals(null, found.cancelCode)
    }

    @Test
    fun `markCancelledByCustomer leaves a pending order and someone else's order alone`() {
        val userId = UUID.randomUUID()
        val pending = saveOrder(userId, Instant.parse("2026-10-22T12:00:00Z"), 1, status = OrderStatus.PENDING)
        val submitted = saveOrder(userId, Instant.parse("2026-10-22T12:00:00Z"), 1)

        var pendingUpdates = 0
        var otherUserUpdates = 0
        inTransaction { pendingUpdates = orders.markCancelledByCustomer(pending.id, userId, "Reason", Instant.now()) }
        inTransaction {
            otherUserUpdates = orders.markCancelledByCustomer(submitted.id, UUID.randomUUID(), "Reason", Instant.now())
        }

        assertEquals(0, pendingUpdates)
        assertEquals(0, otherUserUpdates)
        assertEquals(OrderStatus.PENDING, orders.findById(pending.id).get().status)
        assertEquals(OrderStatus.SUBMITTED, orders.findById(submitted.id).get().status)
    }

    @Test
    fun `V6 marks orders cancelled before it as placement failures, and only those`() {
        fun flyway(target: String) = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .schemas("v6_backfill")
            .target(target)
            .load()

        flyway("5").migrate()
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SET search_path TO v6_backfill")
                listOf("CANCELLED" to "cancelled", "SUBMITTED" to "submitted").forEach { (status, key) ->
                    statement.execute(
                        "INSERT INTO orders (id, order_number, user_id, checkout_session_id, cart_id, " +
                            "idempotency_key, status, subtotal, currency, created_at, updated_at) VALUES " +
                            "(gen_random_uuid(), '$key', gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), " +
                            "'$key', '$status', 1, 'USD', now(), now())"
                    )
                }
                flyway("latest").migrate()

                val rows = statement.executeQuery("SELECT status, cancelled_by FROM orders")
                val cancelledBy = buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) }
                assertEquals(mapOf("CANCELLED" to "PLACEMENT", "SUBMITTED" to null), cancelledBy)
            }
        }
    }

    @Test
    fun `racing cancels change the order once`() {
        val threads = Executors.newFixedThreadPool(2)
        try {
            repeat(20) {
                val userId = UUID.randomUUID()
                val saved = saveOrder(userId, Instant.parse("2026-10-22T12:00:00Z"), 1)
                val start = CountDownLatch(2)
                fun racing() = threads.submit<Int> {
                    start.countDown()
                    start.await()
                    TransactionTemplate(transactionManager).execute {
                        orders.markCancelledByCustomer(saved.id, userId, "Changed my mind", Instant.now())
                    }
                }

                val first = racing()
                val second = racing()

                assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS))
            }
        } finally {
            threads.shutdownNow()
        }
    }

    @Test
    fun `findAbandoned finds only pending orders before the cutoff, oldest first, up to the page size`() {
        val userId = UUID.randomUUID()
        val cutoff = Instant.parse("2026-10-22T12:00:00Z")
        val oldest = saveOrder(userId, cutoff.minusSeconds(120), 1, status = OrderStatus.PENDING)
        val older = saveOrder(userId, cutoff.minusSeconds(60), 1, status = OrderStatus.PENDING)
        saveOrder(userId, cutoff.minusSeconds(30), 1, status = OrderStatus.PENDING)
        saveOrder(userId, cutoff, 1, status = OrderStatus.PENDING)
        saveOrder(userId, cutoff.minusSeconds(180), 1, status = OrderStatus.SUBMITTED)
        saveOrder(userId, cutoff.minusSeconds(180), 1, status = OrderStatus.CANCELLED)

        val found = orders.findAbandoned(cutoff, PageRequest.of(0, 2))

        assertEquals(listOf(oldest.id, older.id), found.map { it.id })
    }

    @Test
    fun `a racing submit and cancel leave the order in exactly one of the two states`() {
        val threads = Executors.newFixedThreadPool(2)
        try {
            repeat(20) { attempt ->
                val saved = orders.save(order(idempotencyKey = "race-$attempt"))
                val start = CountDownLatch(2)
                fun racing(update: () -> Int) = threads.submit<Int> {
                    start.countDown()
                    start.await()
                    TransactionTemplate(transactionManager).execute { update() }
                }

                val submit = racing { orders.markSubmitted(saved.id, Instant.now()) }
                val cancel = racing { orders.markCancelled(saved.id, "PLACEMENT_ABANDONED", Instant.now()) }
                val submitted = submit.get(10, TimeUnit.SECONDS)
                val cancelled = cancel.get(10, TimeUnit.SECONDS)

                assertEquals(1, submitted + cancelled)
                val expected = if (submitted == 1) OrderStatus.SUBMITTED else OrderStatus.CANCELLED
                assertEquals(expected, orders.findById(saved.id).get().status)
            }
        } finally {
            threads.shutdownNow()
        }
    }
}
