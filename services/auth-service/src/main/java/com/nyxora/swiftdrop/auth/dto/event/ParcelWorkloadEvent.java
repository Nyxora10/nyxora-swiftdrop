package com.nyxora.swiftdrop.auth.dto.event;

import java.io.Serializable;
import java.util.UUID;

/**
 * Event DTO received from swiftdrop.events.topic for courier workload synchronization (Member 5: Task 2.2).
 */
public record ParcelWorkloadEvent(
        UUID courierId,
        UUID parcelId,
        String trackingNumber,
        String status
) implements Serializable {
}

