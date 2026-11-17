package com.acme.notification.infrastructure.projection

import com.acme.notification.api.v1.dto.NotificationStatusResponse
import java.util.UUID

/**
 * What goes to `notification.read-model` through the outbox (PIN-549, ADR-0068): a delivery's
 * state as of one moment, for the projector only.
 *
 * The snapshot is the lookup endpoints' response type, so a read is deserialization with no mapping.
 *
 * @property sourceEventId The event the snapshot follows, or a fresh id when no event caused it.
 */
data class DeliverySnapshotMessage(
    val sourceEventId: UUID,
    val notificationId: UUID,
    val snapshot: NotificationStatusResponse
) {
    companion object {
        const val EVENT_TYPE = "NotificationDeliverySnapshot"
        const val TOPIC = "notification.read-model"
    }
}
