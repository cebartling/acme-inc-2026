package com.acme.customer.infrastructure.projection

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.bson.Document
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.util.UUID

/** The read model has not yet applied the change the caller wrote, and did not within the wait. */
class ReadModelBehindException(val readAfter: UUID) :
    RuntimeException("The read model has not applied event $readAfter yet")

/**
 * Reads customer snapshots from the MongoDB read model.
 *
 * Every lookup takes an optional [readAfter]: the event id a write returned in `X-Read-After`.
 * When given, the lookup waits until the projector has applied that event, then returns the
 * snapshot, so a client reads its own write. If the event does not arrive within the wait it
 * throws [ReadModelBehindException] rather than answering with stale data or a false 404.
 */
@Component
class CustomerReadModelQueries(
    private val mongoTemplate: MongoTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${customer.read-model.wait-timeout-ms:5000}")
    private val waitTimeoutMs: Long,
    @Value("\${customer.read-model.poll-interval-ms:25}")
    private val pollIntervalMs: Long
) {
    private val jsonSettings = JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build()

    @PostConstruct
    fun ensureIndexes() {
        val indexes = mongoTemplate.indexOps(CustomerReadModelDocument.COLLECTION)
        indexes.ensureIndex(Index().on(CustomerReadModelDocument.USER_ID, Sort.Direction.ASC).unique())
        indexes.ensureIndex(Index().on(CustomerReadModelDocument.CUSTOMER_NUMBER, Sort.Direction.ASC).unique())
        indexes.ensureIndex(Index().on(CustomerReadModelDocument.EMAIL, Sort.Direction.ASC))
    }

    fun findById(customerId: UUID, readAfter: UUID? = null): CustomerSnapshot? =
        find(Criteria.where(CustomerReadModelDocument.ID).`is`(customerId.toString()), readAfter)

    fun findByUserId(userId: UUID, readAfter: UUID? = null): CustomerSnapshot? =
        find(Criteria.where(CustomerReadModelDocument.USER_ID).`is`(userId.toString()), readAfter)

    fun findByCustomerNumber(customerNumber: String, readAfter: UUID? = null): CustomerSnapshot? =
        find(Criteria.where(CustomerReadModelDocument.CUSTOMER_NUMBER).`is`(customerNumber), readAfter)

    fun findByEmail(email: String, readAfter: UUID? = null): CustomerSnapshot? =
        find(Criteria.where(CustomerReadModelDocument.EMAIL).`is`(email), readAfter)

    private fun find(criteria: Criteria, readAfter: UUID?): CustomerSnapshot? {
        val deadline = System.nanoTime() + waitTimeoutMs * 1_000_000
        while (true) {
            val document = mongoTemplate.findOne(
                Query(criteria),
                Document::class.java,
                CustomerReadModelDocument.COLLECTION
            )
            if (readAfter == null) {
                return document?.let { toSnapshot(it) }
            }
            if (document != null && hasApplied(document, readAfter)) {
                return toSnapshot(document)
            }
            if (System.nanoTime() >= deadline) {
                throw ReadModelBehindException(readAfter)
            }
            Thread.sleep(pollIntervalMs)
        }
    }

    private fun hasApplied(document: Document, eventId: UUID): Boolean =
        document.getList(CustomerReadModelDocument.APPLIED_EVENT_IDS, String::class.java)
            ?.contains(eventId.toString()) == true

    private fun toSnapshot(document: Document): CustomerSnapshot {
        val json = document.get(CustomerReadModelDocument.SNAPSHOT, Document::class.java).toJson(jsonSettings)
        return objectMapper.readValue(json, CustomerSnapshot::class.java)
    }
}
