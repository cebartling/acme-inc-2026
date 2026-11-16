package com.acme.cart.infrastructure.projection

import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.CartStatus
import jakarta.annotation.PostConstruct
import org.bson.Document
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.Semaphore

/** The read model has not yet applied the change the caller wrote, and did not within the wait. */
class ReadModelBehindException(val readAfter: UUID) :
    RuntimeException("The read model has not applied event $readAfter yet")

/**
 * Reads an owner's current cart from the MongoDB read model (PIN-551).
 *
 * [findCurrent] takes an optional `readAfter`: the event id a write returned in `X-Read-After`.
 * When given, it waits until the projector has applied that event, then reads, so a shopper sees
 * their own change. If the event does not arrive within the wait it throws
 * [ReadModelBehindException] rather than answering with a stale cart or a false "no cart".
 */
@Component
class CartReadModelQueries(
    private val mongoTemplate: MongoTemplate,
    @Value("\${acme.cart.read-model.wait-timeout-ms:5000}")
    private val waitTimeoutMs: Long,
    @Value("\${acme.cart.read-model.poll-interval-ms:25}")
    private val pollIntervalMs: Long,
    @Value("\${acme.cart.read-model.max-concurrent-waits:50}")
    maxConcurrentWaits: Int = 50
) {
    // GET /carts/current needs no sign-in, so anyone can send an event id that never arrives.
    // Capping the waits keeps those requests from using up the request threads.
    private val waits = Semaphore(maxConcurrentWaits)

    @PostConstruct
    fun ensureIndexes() {
        val indexes = mongoTemplate.indexOps(CartReadModelDocument.COLLECTION)
        indexes.ensureIndex(Index().on(CartReadModelDocument.USER_ID, Sort.Direction.ASC))
        indexes.ensureIndex(Index().on(CartReadModelDocument.SESSION_ID, Sort.Direction.ASC))
        // The wait looks a write's event id up across every cart
        indexes.ensureIndex(Index().on(CartReadModelDocument.APPLIED_EVENT_IDS, Sort.Direction.ASC))
    }

    /**
     * The `GET /carts/current` response body for [owner]'s ACTIVE or CHECKOUT cart, or null when
     * the owner has none (as after their cart was converted into an order).
     */
    fun findCurrent(owner: CartOwner, readAfter: UUID?): String? {
        if (readAfter != null) waitUntilApplied(readAfter)
        val ownerCriteria = when (owner) {
            is CartOwner.Customer -> Criteria.where(CartReadModelDocument.USER_ID).`is`(owner.userId.toString())
            is CartOwner.Guest -> Criteria.where(CartReadModelDocument.SESSION_ID).`is`(owner.sessionId)
        }
        val current = Criteria.where(CartReadModelDocument.STATUS).`in`(CartStatus.CURRENT.map { it.name })
        // An owner can briefly have two current documents: a converted cart whose snapshot has not
        // landed yet, and the new cart. The one that applied the awaited event is the right one.
        val appliedTheAwaitedEvent = readAfter?.let {
            findOne(ownerCriteria, current, Criteria.where(CartReadModelDocument.APPLIED_EVENT_IDS).`is`(it.toString()))
        }
        return (appliedTheAwaitedEvent ?: findOne(ownerCriteria, current))
            ?.getString(CartReadModelDocument.CART_JSON)
    }

    private fun findOne(vararg criteria: Criteria): Document? = mongoTemplate.findOne(
        Query(Criteria().andOperator(*criteria)), Document::class.java, CartReadModelDocument.COLLECTION
    )

    /**
     * Waits for any cart document to carry [eventId]. The change may have ended the owner's cart
     * (converted it), so the owner's current cart is not where to look for it. When the event is
     * not there yet and every wait slot is taken, it throws [ReadModelBehindException] at once.
     */
    private fun waitUntilApplied(eventId: UUID) {
        val applied = Query(Criteria.where(CartReadModelDocument.APPLIED_EVENT_IDS).`is`(eventId.toString()))
        if (mongoTemplate.exists(applied, CartReadModelDocument.COLLECTION)) return
        if (!waits.tryAcquire()) throw ReadModelBehindException(eventId)
        try {
            val deadline = System.nanoTime() + waitTimeoutMs * 1_000_000
            while (!mongoTemplate.exists(applied, CartReadModelDocument.COLLECTION)) {
                if (System.nanoTime() >= deadline) throw ReadModelBehindException(eventId)
                Thread.sleep(pollIntervalMs)
            }
        } finally {
            waits.release()
        }
    }
}
