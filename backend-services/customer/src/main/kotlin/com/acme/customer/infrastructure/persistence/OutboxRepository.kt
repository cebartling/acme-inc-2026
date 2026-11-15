package com.acme.customer.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Repository for managing outbox messages.
 *
 * Messages are saved during transactions; Debezium reads them from the
 * write-ahead log and publishes them to Kafka.
 */
@Repository
interface OutboxRepository : JpaRepository<OutboxMessage, UUID>
