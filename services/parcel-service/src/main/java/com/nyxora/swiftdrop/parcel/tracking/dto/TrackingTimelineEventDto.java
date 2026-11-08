package com.nyxora.swiftdrop.parcel.tracking.dto;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Individual chronological milestone on the parcel delivery tracking timeline.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Milestone entry in the delivery tracking timeline")
public class TrackingTimelineEventDto {

    @Schema(description = "Lifecycle status transition", example = "IN_TRANSIT")
    private ParcelStatus status;

    @Schema(description = "Timestamp when this status transition occurred", example = "2026-11-07T10:15:00Z")
    private Instant timestamp;

    @Schema(description = "Operational note or update description", example = "Parcel departs Colombo Hub en route to Kandy Hub.")
    private String deliveryNote;
}

