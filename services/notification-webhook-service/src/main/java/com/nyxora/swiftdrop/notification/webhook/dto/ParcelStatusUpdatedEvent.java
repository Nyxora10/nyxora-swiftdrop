package com.nyxora.swiftdrop.notification.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Inbound domain event DTO deserialized from RabbitMQ topic exchange (swiftdrop.events.topic)
 * on queue swiftdrop.webhook.parcel.events with routing key parcel.status.updated.
 *
 * Compatible with the event published by parcel-service CourierExecutionServiceImpl.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParcelStatusUpdatedEvent implements Serializable {

    private UUID eventId;
    private String eventType;
    private UUID parcelId;
    private String trackingNumber;
    private String previousStatus;
    private String currentStatus;
    private String status;
    private UUID courierId;
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
