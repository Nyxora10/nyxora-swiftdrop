package com.nyxora.swiftdrop.parcel.websocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Real-Time Tracking Update DTO.
 *
 * Broadcast to public STOMP topic {@code /topic/parcels/{trackingNumber}}
 * to notify tracking page clients immediately upon lifecycle status changes.
 *
 * Privacy Enforcement:
 * - Omits sensitive customer PII (phone number, full address, sender details).
 * - Carries privacy-safe {@code recipientNameMasked} for public verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParcelTrackingUpdateDTO {

    private String trackingNumber;
    private String currentStatus;
    private String statusDescription;
    private String currentZone;
    private String recipientNameMasked;
    private String deliveryNote;
    private String proofPhotoUrl;
    private Instant timestamp;
}

