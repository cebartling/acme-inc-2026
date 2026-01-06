package com.acme.notification.api.v1

import com.acme.notification.api.v1.dto.NotificationStatusResponse
import com.acme.notification.domain.NotificationDelivery
import com.acme.notification.infrastructure.persistence.NotificationDeliveryRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for notification-related endpoints.
 */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val deliveryRepository: NotificationDeliveryRepository
) {

    /**
     * Gets the status of a notification by ID.
     *
     * @param id The notification ID.
     * @return The notification status.
     */
    @GetMapping("/{id}/status")
    fun getNotificationStatus(@PathVariable id: UUID): ResponseEntity<NotificationStatusResponse> {
        val delivery = deliveryRepository.findById(id)
        return if (delivery.isPresent) {
            ResponseEntity.ok(delivery.get().toStatusResponse())
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Gets all notifications for a recipient.
     *
     * @param recipientId The recipient's user ID.
     * @return List of notification statuses.
     */
    @GetMapping("/recipient/{recipientId}")
    fun getNotificationsByRecipient(@PathVariable recipientId: UUID): ResponseEntity<List<NotificationStatusResponse>> {
        val deliveries = deliveryRepository.findByRecipientId(recipientId)
        return ResponseEntity.ok(deliveries.map { it.toStatusResponse() })
    }

    private fun NotificationDelivery.toStatusResponse() = NotificationStatusResponse(
        notificationId = id,
        notificationType = notificationType,
        recipientId = recipientId,
        recipientEmail = recipientEmail,
        status = status,
        attemptCount = attemptCount,
        sentAt = sentAt,
        deliveredAt = deliveredAt,
        bouncedAt = bouncedAt,
        bounceReason = bounceReason,
        createdAt = createdAt
    )
}
