package com.nyxora.swiftdrop.notification.consumer;

import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import com.nyxora.swiftdrop.notification.dispatcher.NotificationDispatcher;
import com.nyxora.swiftdrop.notification.dto.event.ParcelStatusUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Asynchronous AMQP consumer that listens for parcel lifecycle events on RabbitMQ queue
 * 'notification.parcel.events' (bound to topic exchange 'swiftdrop.events.topic' with key 'parcel.#').
 *
 * Responsibilities:
 * - Ingests and deserializes ParcelStatusUpdatedEvent payloads.
 * - Performs defensive validation against corrupt or null events to prevent queue poisoning.
 * - Protects customer privacy by masking PII (email, phone) in diagnostic logs using PiiMaskingUtil.
 * - Dispatches validated events to the NotificationDispatcher.
 * - Contains exception boundaries to prevent poisonous retry storms on the broker.
 */
@Component
@Slf4j
public class ParcelStatusNotificationConsumer {

    private final NotificationDispatcher notificationDispatcher;

    public ParcelStatusNotificationConsumer(
            @Autowired(required = false) NotificationDispatcher notificationDispatcher) {
        this.notificationDispatcher = notificationDispatcher != null
                ? notificationDispatcher
                : event -> log.info("No concrete NotificationDispatcher registered yet. Event validated successfully: trackingNumber={}",
                event.getTrackingNumber());
    }

    @RabbitListener(queues = "${app.rabbitmq.notification-queue:notification.parcel.events}")
    public void handleParcelStatusEvent(ParcelStatusUpdatedEvent event) {
        if (event == null) {
            log.warn("Discarding null ParcelStatusUpdatedEvent from notification queue.");
            return;
        }

        if (event.getTrackingNumber() == null || event.getTrackingNumber().isBlank()) {
            log.warn("Discarding malformed ParcelStatusUpdatedEvent: missing trackingNumber. EventId={}", event.getEventId());
            return;
        }

        if (event.getCurrentStatus() == null || event.getCurrentStatus().isBlank()) {
            log.warn("Discarding malformed ParcelStatusUpdatedEvent: missing currentStatus for trackingNumber={}",
                    event.getTrackingNumber());
            return;
        }

        String maskedEmail = PiiMaskingUtil.maskEmail(event.getRecipientEmail());
        String maskedPhone = PiiMaskingUtil.maskPhoneNumber(event.getRecipientPhone());

        log.info("Successfully received AMQP parcel status event: trackingNumber={}, status={}, recipientEmail={}, recipientPhone={}",
                event.getTrackingNumber(),
                event.getCurrentStatus(),
                maskedEmail,
                maskedPhone);

        try {
            notificationDispatcher.dispatch(event);
        } catch (Exception ex) {
            log.error("Unhandled exception while dispatching notification for trackingNumber={}: {}",
                    event.getTrackingNumber(),
                    ex.getMessage(),
                    ex);
        }
    }

    public NotificationDispatcher getNotificationDispatcher() {
        return notificationDispatcher;
    }
}

