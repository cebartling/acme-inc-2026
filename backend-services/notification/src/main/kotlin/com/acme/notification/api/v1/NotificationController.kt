package com.acme.notification.api.v1

import com.acme.notification.api.v1.dto.NotificationStatusResponse
import com.acme.notification.infrastructure.projection.NotificationReadModelQueries
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for notification-related endpoints.
 *
 * The lookups answer from the MongoDB read model (PIN-549, ADR-0068), so a delivery shows up a
 * moment after it is sent, once the projector has applied its snapshot. A client that needs to see
 * a delivery it just caused polls until it appears.
 */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val readModel: NotificationReadModelQueries
) {

    /**
     * Gets the status of a notification by ID.
     *
     * @param id The notification ID.
     * @return The notification status.
     */
    @GetMapping("/{id}/status")
    fun getNotificationStatus(@PathVariable id: UUID): ResponseEntity<NotificationStatusResponse> {
        val snapshot = readModel.findById(id)
        return if (snapshot != null) {
            ResponseEntity.ok(snapshot)
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
    fun getNotificationsByRecipient(@PathVariable recipientId: UUID): ResponseEntity<List<NotificationStatusResponse>> =
        ResponseEntity.ok(readModel.findByRecipientId(recipientId))

    /**
     * Gets all notifications for a recipient by email address.
     *
     * @param email The recipient's email address.
     * @return List of notification statuses.
     */
    @GetMapping("/by-email/{email}")
    fun getNotificationsByEmail(@PathVariable email: String): ResponseEntity<List<NotificationStatusResponse>> =
        ResponseEntity.ok(readModel.findByRecipientEmail(email))
}
