package com.acme.notification.infrastructure.projection

import com.acme.notification.api.v1.dto.NotificationStatusResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.bson.Document
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Reads delivery snapshots from the MongoDB read model (PIN-549, ADR-0068). The lookups are
 * eventually consistent: a delivery shows up once the projector has applied its snapshot.
 */
@Component
class NotificationReadModelQueries(
    private val mongoTemplate: MongoTemplate,
    private val objectMapper: ObjectMapper
) {
    private val jsonSettings = JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build()

    @PostConstruct
    fun ensureIndexes() {
        val indexes = mongoTemplate.indexOps(NotificationReadModelDocument.COLLECTION)
        indexes.ensureIndex(Index().on(NotificationReadModelDocument.RECIPIENT_ID, Sort.Direction.ASC))
        indexes.ensureIndex(Index().on(NotificationReadModelDocument.RECIPIENT_EMAIL, Sort.Direction.ASC))
    }

    fun findById(notificationId: UUID): NotificationStatusResponse? =
        mongoTemplate.findOne(
            Query(Criteria.where(NotificationReadModelDocument.ID).`is`(notificationId.toString())),
            Document::class.java,
            NotificationReadModelDocument.COLLECTION
        )?.let { toSnapshot(it) }

    fun findByRecipientId(recipientId: UUID): List<NotificationStatusResponse> =
        findAll(Criteria.where(NotificationReadModelDocument.RECIPIENT_ID).`is`(recipientId.toString()))

    fun findByRecipientEmail(email: String): List<NotificationStatusResponse> =
        findAll(Criteria.where(NotificationReadModelDocument.RECIPIENT_EMAIL).`is`(email))

    /** Oldest first, as the PostgreSQL lookups returned them in insertion order. */
    private fun findAll(criteria: Criteria): List<NotificationStatusResponse> =
        mongoTemplate.find(
            Query(criteria).with(Sort.by(Sort.Direction.ASC, NotificationReadModelDocument.CREATED_AT)),
            Document::class.java,
            NotificationReadModelDocument.COLLECTION
        ).map { toSnapshot(it) }

    private fun toSnapshot(document: Document): NotificationStatusResponse {
        val json = document.get(NotificationReadModelDocument.SNAPSHOT, Document::class.java).toJson(jsonSettings)
        return objectMapper.readValue(json, NotificationStatusResponse::class.java)
    }
}
