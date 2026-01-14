package com.acme.customer.application

import com.acme.customer.domain.Customer
import com.acme.customer.domain.ProfileCompleteness
import com.acme.customer.domain.events.ProfileCompleted
import com.acme.customer.infrastructure.messaging.OutboxWriter
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.persistence.EventStoreRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Service for checking and handling profile completion.
 *
 * This service is responsible for:
 * 1. Checking if a customer's profile has reached 100% completion
 * 2. Publishing the ProfileCompleted event when completion is first achieved
 * 3. Tracking metrics for profile completions
 *
 * It should be called after any profile-related update (profile, address,
 * preferences, consent) to check if the profile is now complete.
 */
@Service
class ProfileCompletionService(
    private val profileCompletenessCalculator: ProfileCompletenessCalculator,
    private val customerRepository: CustomerRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val outboxWriter: OutboxWriter,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(ProfileCompletionService::class.java)

    private val profileCompletedCounter: Counter = Counter.builder("profile.completed")
        .description("Total number of profiles that reached 100% completion")
        .register(meterRegistry)

    private val profileCompletenessGauge = meterRegistry.summary("profile.completeness.distribution")

    /**
     * Checks and updates profile completion status for a customer.
     *
     * This method:
     * 1. Calculates the current profile completeness
     * 2. Updates the customer's profileCompleteness field if changed
     * 3. Publishes a ProfileCompleted event if the profile just reached 100%
     *
     * @param customerId The customer ID to check.
     * @param previousScore The previous profile completeness score (before the recent update).
     * @param correlationId Correlation ID for distributed tracing.
     * @param causationId The event ID that caused this check.
     * @return The calculated profile completeness.
     */
    @Transactional
    fun checkAndUpdateCompletion(
        customerId: UUID,
        previousScore: Int,
        correlationId: UUID,
        causationId: UUID? = null
    ): ProfileCompleteness? {
        logger.debug("Checking profile completion for customer: {}", customerId)

        val customer = customerRepository.findById(customerId).orElse(null)
            ?: run {
                logger.warn("Customer not found when checking completion: {}", customerId)
                return null
            }

        val completeness = profileCompletenessCalculator.calculate(customer)

        // Record completeness distribution for metrics
        profileCompletenessGauge.record(completeness.overallScore.toDouble())

        // Update the stored profileCompleteness if it changed
        if (completeness.overallScore != customer.profileCompleteness) {
            customer.profileCompleteness = completeness.overallScore
            customerRepository.save(customer)

            logger.info(
                "Profile completeness updated for customer {}: {} -> {}",
                customerId,
                previousScore,
                completeness.overallScore
            )
        }

        // Check for first-time 100% completion
        if (previousScore < 100 && completeness.overallScore >= 100) {
            publishProfileCompletedEvent(customer, correlationId, causationId)
        }

        return completeness
    }

    /**
     * Publishes a ProfileCompleted event for a customer.
     *
     * @param customer The customer who completed their profile.
     * @param correlationId Correlation ID for distributed tracing.
     * @param causationId The event that caused this completion.
     */
    private fun publishProfileCompletedEvent(
        customer: Customer,
        correlationId: UUID,
        causationId: UUID?
    ) {
        logger.info("Profile completed for customer: {}", customer.id)

        val event = ProfileCompleted.create(
            customerId = customer.id,
            registeredAt = customer.registeredAt,
            correlationId = correlationId,
            causationId = causationId
        )

        // Persist event to event store
        eventStoreRepository.append(event)

        // Write to outbox for Kafka publishing
        outboxWriter.write(event, ProfileCompleted.TOPIC)

        // Increment completion counter
        profileCompletedCounter.increment()

        logger.info(
            "Published ProfileCompleted event {} for customer {}. Time to complete: {}",
            event.eventId,
            customer.id,
            event.payload.timeToComplete
        )
    }

    /**
     * Calculates profile completeness for a customer without updating or publishing events.
     *
     * @param customerId The customer ID.
     * @return The calculated profile completeness, or null if customer not found.
     */
    fun getCompleteness(customerId: UUID): ProfileCompleteness? {
        val customer = customerRepository.findById(customerId).orElse(null)
            ?: return null

        return profileCompletenessCalculator.calculate(customer)
    }
}
