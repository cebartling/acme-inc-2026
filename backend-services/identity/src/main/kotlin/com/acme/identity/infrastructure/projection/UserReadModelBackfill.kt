package com.acme.identity.infrastructure.projection

import com.acme.identity.infrastructure.messaging.OutboxWriter
import com.acme.identity.infrastructure.persistence.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

data class BackfillResult(val total: Int, val written: Int, val failed: Int)

/**
 * Gives every user that predates the read model a MongoDB document (PIN-548).
 *
 * The Debezium connector starts with `snapshot.mode: no_data`, so it publishes only the outbox rows
 * written after it started. A user who has not changed since has no document. This writes a snapshot
 * row to the outbox for each user, and the projector does the rest, as it does for any change.
 *
 * It is off unless `identity.read-model.backfill-on-startup` is true, so a run is deliberate. It is
 * safe to run again: a snapshot replaces the document. Run it before traffic, or when it is quiet: a
 * user's own change that commits between the backfill reading their state and writing its row is
 * overwritten with the older state, until that user changes again.
 */
@Component
@ConditionalOnProperty(name = ["identity.read-model.backfill-on-startup"], havingValue = "true")
class UserReadModelBackfill(
    private val userRepository: UserRepository,
    private val outboxWriter: OutboxWriter,
    @Value("\${identity.read-model.backfill-page-size:500}")
    private val pageSize: Int
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(UserReadModelBackfill::class.java)

    override fun run(args: ApplicationArguments) {
        backfill()
    }

    fun backfill(): BackfillResult {
        logger.info("Backfilling the users read model")
        var written = 0
        var failed = 0
        var total = 0
        var pageNumber = 0
        do {
            val page = userRepository.findAll(PageRequest.of(pageNumber++, pageSize, Sort.by("id")))
            total = page.totalElements.toInt()
            page.content.forEach { user ->
                try {
                    outboxWriter.writeSnapshot(user.id, AGGREGATE_TYPE)
                    written++
                } catch (e: Exception) {
                    failed++
                    logger.error("Could not backfill user {}: {}", user.id, e.message, e)
                }
            }
        } while (page.hasNext())

        val result = BackfillResult(total, written, failed)
        logger.info("Backfilled the users read model: {}", result)
        return result
    }

    private companion object {
        const val AGGREGATE_TYPE = "User"
    }
}
