package com.nyxora.swiftdrop.ai_assistant_service.dto.tracking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTO representing the actual response payload returned by the Parcel Service
 * from the endpoint {@code GET /api/v1/parcels/track/{trackingNumber}}.
 */
public record ParcelTrackingResponseDto(
        String trackingNumber,
        String currentStatus,
        String originZoneCode,
        String originZoneName,
        String destinationZoneCode,
        String destinationZoneName,
        String maskedRecipientName,
        String maskedRecipientPhone,
        String maskedDropoffAddress,
        BigDecimal weightKg,
        String parcelSize,
        Instant createdAt,
        List<TrackingTimelineEventDto> timeline
) {}

