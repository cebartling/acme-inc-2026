package com.acme.customer.infrastructure.tracking

import com.acme.customer.infrastructure.persistence.CustomerRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Service for asynchronously tracking customer activity.
 *
 * Updates the `lastActivityAt` timestamp for customers without blocking
 * API responses. Failures are logged but do not propagate to the caller,
 * as activity tracking is a non-critical operation.
 */
@Service
class CustomerActivityTracker(
    private val customerRepository: CustomerRepository
) {
    private val logger = LoggerFactory.getLogger(CustomerActivityTracker::class.java)

    /**
     * Asynchronously updates the last activity timestamp for a customer.
     *
     * This method:
     * - Runs in a separate thread pool (via @Async)
     * - Does not block the calling thread
     * - Logs errors but does not throw exceptions
     *
     * @param userId The user ID whose activity should be tracked.
     */
    @Async
    fun trackAsync(userId: UUID) {
        try {
            logger.debug("Tracking activity for user: {}", userId)

            val customer = customerRepository.findByUserId(userId)
            if (customer == null) {
                logger.warn("Cannot track activity: customer not found for user {}", userId)
                return
            }

            val now = Instant.now()
            customer.lastActivityAt = now
            customer.updatedAt = now

            customerRepository.save(customer)

            logger.debug("Updated lastActivityAt for user {} to {}", userId, now)
        } catch (e: Exception) {
            logger.error(
                "Failed to track activity for user {}: {}",
                userId,
                e.message,
                e
            )
            // Don't throw - this is async and non-critical
            // Failures are logged for monitoring but don't affect the user
        }
    }
}
