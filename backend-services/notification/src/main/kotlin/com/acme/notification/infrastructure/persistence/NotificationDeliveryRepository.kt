package com.acme.notification.infrastructure.persistence

import com.acme.notification.domain.NotificationDelivery
import com.acme.notification.domain.NotificationStatus
import com.acme.notification.domain.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * JPA repository for [NotificationDelivery] entities.
 *
 * Provides CRUD operations and custom queries for notification
 * delivery tracking.
 */
@Repository
interface NotificationDeliveryRepository : JpaRepository<NotificationDelivery, UUID> {

    /**
     * Finds all deliveries for a specific recipient.
     *
     * @param recipientId The user ID of the recipient.
     * @return List of delivery records for the recipient.
     */
    fun findByRecipientId(recipientId: UUID): List<NotificationDelivery>

    /**
     * Finds all deliveries with a specific status.
     *
     * @param status The delivery status to filter by.
     * @return List of delivery records with the given status.
     */
    fun findByStatus(status: NotificationStatus): List<NotificationDelivery>

    /**
     * Finds a delivery by correlation ID.
     *
     * @param correlationId The correlation ID for distributed tracing.
     * @return The delivery record if found.
     */
    fun findByCorrelationId(correlationId: String): NotificationDelivery?

    /**
     * Finds deliveries that need retry (failed but not exceeded max attempts).
     *
     * @param status The status to filter by (typically PENDING or FAILED).
     * @param maxAttempts Maximum number of retry attempts.
     * @return List of delivery records eligible for retry.
     */
    @Query(
        """
        SELECT d FROM NotificationDelivery d
        WHERE d.status = :status
        AND d.attemptCount < :maxAttempts
        ORDER BY d.createdAt ASC
        """
    )
    fun findEligibleForRetry(status: NotificationStatus, maxAttempts: Int): List<NotificationDelivery>

    /**
     * Checks if a notification has been sent for a specific recipient and type.
     *
     * @param recipientId The user ID of the recipient.
     * @param notificationType The type of notification.
     * @return true if a notification exists.
     */
    fun existsByRecipientIdAndNotificationType(
        recipientId: UUID,
        notificationType: NotificationType
    ): Boolean
}
