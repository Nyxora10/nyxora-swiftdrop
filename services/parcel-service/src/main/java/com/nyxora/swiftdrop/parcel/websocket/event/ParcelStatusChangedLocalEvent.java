package com.nyxora.swiftdrop.parcel.websocket.event;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import lombok.Builder;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Internal Spring application domain event published within parcel-service
 * when a courier status update or automated lifecycle transition occurs.
 *
 * Processed by the post-commit transactional event listener to trigger
 * real-time WebSocket broadcasts only upon successful database commit.
 */
@Builder
public record ParcelStatusChangedLocalEvent(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus previousStatus,
        ParcelStatus newStatus,
        String statusDescription,
        String currentZone,
        String recipientName,
        String deliveryNote,
        String proofPhotoUrl,
        Instant timestamp
) implements Serializable {}

