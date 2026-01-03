package com.acme.identity.domain.events

import com.acme.identity.domain.RegistrationSource
import java.time.Instant
import java.util.UUID

data class UserRegisteredPayload(
    val userId: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val tosAcceptedAt: Instant,
    val marketingOptIn: Boolean,
    val registrationSource: RegistrationSource
)

class UserRegistered(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: UserRegisteredPayload
) : DomainEvent(
    eventId = eventId,
    eventType = EVENT_TYPE,
    eventVersion = EVENT_VERSION,
    timestamp = timestamp,
    aggregateId = aggregateId,
    aggregateType = AGGREGATE_TYPE,
    correlationId = correlationId
) {
    companion object {
        const val EVENT_TYPE = "UserRegistered"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "User"
        const val TOPIC = "identity.user.events"

        fun create(
            userId: UUID,
            email: String,
            firstName: String,
            lastName: String,
            tosAcceptedAt: Instant,
            marketingOptIn: Boolean,
            registrationSource: RegistrationSource,
            correlationId: UUID = UUID.randomUUID()
        ): UserRegistered {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return UserRegistered(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = userId,
                correlationId = correlationId,
                payload = UserRegisteredPayload(
                    userId = userId,
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    tosAcceptedAt = tosAcceptedAt,
                    marketingOptIn = marketingOptIn,
                    registrationSource = registrationSource
                )
            )
        }
    }
}
