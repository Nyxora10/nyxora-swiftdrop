package com.nyxora.swiftdrop.parcel.dispatch.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Asynchronous domain event published to swiftdrop.events.topic with routing key "parcel.assigned".
 * Consumed by auth-service to atomically increment the courier's active_delivery_count.
 */
public record ParcelAssignedEvent(
        UUID courierId,
        UUID parcelId,
        String trackingNumber,
        String status,
        Instant assignedAt
) implements Serializable {
}
