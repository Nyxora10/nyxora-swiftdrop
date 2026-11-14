package com.nyxora.swiftdrop.ai_assistant_service.dto.tracking;

import java.time.Instant;

/**
 * DTO representing an individual milestone event on the parcel tracking timeline,
 * matching the schema returned by the downstream Parcel Service.
 */
public record TrackingTimelineEventDto(
        String status,
        Instant timestamp,
        String deliveryNote
) {}

