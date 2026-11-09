package com.nyxora.swiftdrop.parcel.courier.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event published to RabbitMQ topic exchange (swiftdrop.events.topic)
 * whenever a courier transitions a parcel's delivery status.
 *
 * Consumed by:
 * - notification-webhook-service (Member 7: Task 1.10 - customer notifications & merchant webhooks)
 * - auth-service (Member 5: Task 2.2 - courier active workload tracking)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParcelStatusUpdatedEvent implements Serializable {

    private UUID eventId;
    private String eventType;
    private UUID parcelId;
    private String trackingNumber;
    private String previousStatus;
    private String currentStatus;
    private String status; // Alias for currentStatus (consumed by auth-service workload tracker)
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