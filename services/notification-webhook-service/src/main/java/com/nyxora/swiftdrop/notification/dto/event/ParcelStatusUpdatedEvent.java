package com.nyxora.swiftdrop.notification.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable parcel status event received from parcel-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParcelStatusUpdatedEvent(
        UUID eventId,
        String eventType,
        UUID parcelId,
        String trackingNumber,
        String previousStatus,
        String currentStatus,
        UUID merchantId,
        UUID senderUserId,
        String senderEmail,
        String recipientName,
        String recipientEmail,
        String recipientPhone,
        String courierName,
        String courierPhone,
        String deliveryNote,
        String failureReason,
        Instant timestamp) {

    public static Builder builder() {
        return new Builder();
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public UUID getParcelId() { return parcelId; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getPreviousStatus() { return previousStatus; }
    public String getCurrentStatus() { return currentStatus; }
    public UUID getMerchantId() { return merchantId; }
    public UUID getSenderUserId() { return senderUserId; }
    public String getSenderEmail() { return senderEmail; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getCourierName() { return courierName; }
    public String getCourierPhone() { return courierPhone; }
    public String getDeliveryNote() { return deliveryNote; }
    public String getFailureReason() { return failureReason; }
    public Instant getTimestamp() { return timestamp; }

    public ParcelStatusUpdatedEvent withSenderEmail(String value) {
        return copy(value, recipientEmail, recipientPhone, courierName, timestamp, currentStatus);
    }

    public ParcelStatusUpdatedEvent withRecipientEmail(String value) {
        return copy(senderEmail, value, recipientPhone, courierName, timestamp, currentStatus);
    }

    public ParcelStatusUpdatedEvent withRecipientPhone(String value) {
        return copy(senderEmail, recipientEmail, value, courierName, timestamp, currentStatus);
    }

    public ParcelStatusUpdatedEvent withCourierName(String value) {
        return copy(senderEmail, recipientEmail, recipientPhone, value, timestamp, currentStatus);
    }

    public ParcelStatusUpdatedEvent withCurrentStatus(String value) {
        return copy(senderEmail, recipientEmail, recipientPhone, courierName, timestamp, value);
    }

    public ParcelStatusUpdatedEvent withTimestamp(Instant value) {
        return copy(senderEmail, recipientEmail, recipientPhone, courierName, value, currentStatus);
    }

    private ParcelStatusUpdatedEvent copy(String senderEmail, String recipientEmail, String recipientPhone,
                                          String courierName, Instant timestamp, String currentStatus) {
        return new ParcelStatusUpdatedEvent(eventId, eventType, parcelId, trackingNumber, previousStatus,
                currentStatus, merchantId, senderUserId, senderEmail, recipientName, recipientEmail,
                recipientPhone, courierName, courierPhone, deliveryNote, failureReason, timestamp);
    }

    public static final class Builder {
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

        public Builder eventId(UUID value) { eventId = value; return this; }
        public Builder eventType(String value) { eventType = value; return this; }
        public Builder parcelId(UUID value) { parcelId = value; return this; }
        public Builder trackingNumber(String value) { trackingNumber = value; return this; }
        public Builder previousStatus(String value) { previousStatus = value; return this; }
        public Builder currentStatus(String value) { currentStatus = value; return this; }
        public Builder merchantId(UUID value) { merchantId = value; return this; }
        public Builder senderUserId(UUID value) { senderUserId = value; return this; }
        public Builder senderEmail(String value) { senderEmail = value; return this; }
        public Builder recipientName(String value) { recipientName = value; return this; }
        public Builder recipientEmail(String value) { recipientEmail = value; return this; }
        public Builder recipientPhone(String value) { recipientPhone = value; return this; }
        public Builder courierName(String value) { courierName = value; return this; }
        public Builder courierPhone(String value) { courierPhone = value; return this; }
        public Builder deliveryNote(String value) { deliveryNote = value; return this; }
        public Builder failureReason(String value) { failureReason = value; return this; }
        public Builder timestamp(Instant value) { timestamp = value; return this; }

        public ParcelStatusUpdatedEvent build() {
            return new ParcelStatusUpdatedEvent(eventId, eventType, parcelId, trackingNumber, previousStatus,
                    currentStatus, merchantId, senderUserId, senderEmail, recipientName, recipientEmail,
                    recipientPhone, courierName, courierPhone, deliveryNote, failureReason, timestamp);
        }
    }
}
