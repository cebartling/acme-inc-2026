package com.acme.notification.infrastructure.messaging

/**
 * Writing to the outbox failed (PIN-549, ADR-0068).
 *
 * The send use cases turn any other exception into a `Failure` result. This one they rethrow, so
 * the handler fails and Kafka redelivers the event, instead of the delivery committing without its
 * event.
 */
class OutboxWriteException(message: String, cause: Throwable) : RuntimeException(message, cause)
