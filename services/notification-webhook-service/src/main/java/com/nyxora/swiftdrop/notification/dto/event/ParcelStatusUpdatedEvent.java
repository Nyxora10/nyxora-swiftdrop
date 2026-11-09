package com.nyxora.swiftdrop.notification.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event DTO representing an asynchronous parcel status change emitted by parcel-service.
 * Consumed by notification-webhook-service from RabbitMQ exchange swiftdrop.events.topic
 * via queue notification.parcel.events.
 *
 * Adheres to the Zero Cross-Database Query Rule:
 * Carries all necessary context (recipient email, sender email, courier info, tracking details)
 * so that notification-webhook-service does not need to query parcel_db or auth_db.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParcelStatusUpdatedEvent {

    private UUID eventId;
    private String eventType;
    private UUID parcelId;
    private String trackingNumber;
    private String previousStatus;
    private String currentStatus;
    private UUID merchantId;
    private UUID senderUserId;
    private String senderEmail;
    private String recipientName;
    private String recipientEmail;
    private String recipientPhone;
    private String courierName;
    private String courierPhone;
    private String deliveryNote;
    private String failureReason;
    private Instant timestamp;
}

