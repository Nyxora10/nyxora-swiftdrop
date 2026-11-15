package com.nyxora.swiftdrop.notification.webhook.consumer;

import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import com.nyxora.swiftdrop.notification.webhook.config.WebhookRabbitConfig;
import com.nyxora.swiftdrop.notification.webhook.dto.ParcelStatusUpdatedEvent;
import com.nyxora.swiftdrop.notification.webhook.dto.WebhookDispatchResult;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.nyxora.swiftdrop.notification.webhook.repository.WebhookDeliveryLogRepository;
import com.nyxora.swiftdrop.notification.webhook.retry.WebhookRetryPublisher;
import com.nyxora.swiftdrop.notification.webhook.service.WebhookDispatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Dedicated RabbitMQ consumer for merchant parcel status update events.
 * Listens on queue swiftdrop.webhook.parcel.events (bound to swiftdrop.events.topic
 * on routing key parcel.status.updated).
 *
 * Responsibilities:
 * - Acknowledges and skips retail customer parcels (merchantId == null).
 * - For enterprise merchant parcels (merchantId != null), invokes WebhookDispatcherService.
 * - If dispatch returns a retryable failure, schedules exponential backoff via WebhookRetryPublisher.
 * - Guards against duplicate deliveries caused by RabbitMQ redeliveries.
 * - Safely handles malformed payloads without infinite requeue loops.
 */
@Component
public class ParcelStatusWebhookConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParcelStatusWebhookConsumer.class);

    private final WebhookDispatcherService dispatcherService;
    private final WebhookRetryPublisher retryPublisher;
    private final WebhookDeliveryLogRepository logRepository;

    @Autowired
    public ParcelStatusWebhookConsumer(
            WebhookDispatcherService dispatcherService,
            WebhookRetryPublisher retryPublisher,
            WebhookDeliveryLogRepository logRepository) {
        this.dispatcherService = dispatcherService;
        this.retryPublisher = retryPublisher;
        this.logRepository = logRepository;
    }

    /**
     * Consumes parcel status updated events and initiates outbound webhook delivery.
     *
     * @param event the inbound deserialized parcel status event
     */
    @RabbitListener(queues = "${app.rabbitmq.webhook-parcel-queue:" + WebhookRabbitConfig.WEBHOOK_PARCEL_EVENTS_QUEUE + "}")
    public void handleParcelStatusUpdated(ParcelStatusUpdatedEvent event) {
        // 1. Validate required fields
        if (event == null || event.getParcelId() == null) {
            log.warn("Discarding malformed or null ParcelStatusUpdatedEvent");
            return;
        }

        // 2. Skip retail parcels (B2C / individual direct customer bookings)
        if (event.getMerchantId() == null) {
            log.debug("Skipping retail parcel event without merchantId: parcelId={}, trackingNumber={}",
                    event.getParcelId(), event.getTrackingNumber());
            return;
        }

        // 3. Duplicate event check (RabbitMQ redelivery protection)
        if (isDuplicateEvent(event)) {
            log.info("Discarding duplicate parcel status event for parcelId={}, trackingNumber={}, status={}",
                    event.getParcelId(), event.getTrackingNumber(), event.getCurrentStatus());
            return;
        }

        // 4. Resolve event classification
        String eventType = (event.getEventType() != null && !event.getEventType().isBlank())
                ? event.getEventType()
                : WebhookRabbitConfig.WEBHOOK_PARCEL_EVENTS_ROUTING_KEY;

        log.info("Processing parcel status event for merchantId={}, parcelId={}, trackingNumber={}, currentStatus={}",
                event.getMerchantId(), event.getParcelId(), event.getTrackingNumber(), event.getCurrentStatus());

        // 5. Execute initial webhook dispatch
        WebhookDispatchResult result;
        try {
            result = dispatcherService.dispatchWebhook(
                    event.getMerchantId(),
                    event.getParcelId(),
                    eventType,
                    event
            );
        } catch (Exception ex) {
            log.error("Unexpected exception dispatching webhook for merchantId={}, parcelId={}: {}",
                    event.getMerchantId(), event.getParcelId(), PiiMaskingUtil.maskText(ex.getMessage()), ex);
            throw new AmqpRejectAndDontRequeueException("Unexpected dispatch error for parcelId=" + event.getParcelId(), ex);
        }

        // 6. Handle dispatch outcome
        if (result.success()) {
            log.info("Initial webhook delivery SUCCEEDED for merchantId={}, parcelId={}, statusCode={}",
                    event.getMerchantId(), event.getParcelId(), result.httpStatusCode());
            return;
        }

        log.warn("Initial webhook delivery FAILED for merchantId={}, parcelId={}, statusCode={}, errorCategory={}, retryable={}",
                event.getMerchantId(), event.getParcelId(), result.httpStatusCode(), result.errorCategory(), result.retryable());

        // 7. Schedule DLQ retry if failure is retryable
        if (result.retryable() && result.deliveryLog() != null) {
            try {
                log.info("Scheduling initial DLQ retry for deliveryLogId={}, merchantId={}",
                        result.deliveryLog().getId(), event.getMerchantId());
                retryPublisher.scheduleRetry(result.deliveryLog());
            } catch (Exception ex) {
                log.error("Failed to schedule DLQ retry for deliveryLogId={}: {}",
                        result.deliveryLog().getId(), PiiMaskingUtil.maskText(ex.getMessage()), ex);
                try {
                    result.deliveryLog().setDeliveryStatus(WebhookDeliveryStatus.DEAD_LETTER);
                    result.deliveryLog().setNextRetryAt(null);
                    logRepository.save(result.deliveryLog());
                } catch (Exception dbEx) {
                    log.error("Failed to mark deliveryLog as DEAD_LETTER for deliveryLogId={}: {}",
                            result.deliveryLog().getId(), PiiMaskingUtil.maskText(dbEx.getMessage()));
                }
                throw new AmqpRejectAndDontRequeueException(
                        "RabbitMQ retry publication failed for deliveryLogId=" + result.deliveryLog().getId(), ex);
            }
        }
    }

    /**
     * Checks whether this event was already processed or is currently in-flight
     * for the same parcel.
     *
     * <p>Deduplication Policy:
     * <ul>
     *   <li>Primary identity is {@code eventId}. When {@code eventId} is present,
     *       only matching {@code eventId} values in previous delivery logs classify
     *       an event as a redelivery duplicate.</li>
     *   <li>Events with different {@code eventId}s for the same parcel (e.g. repeated
     *       transitions such as OUT_FOR_DELIVERY -> FAILED -> OUT_FOR_DELIVERY) are
     *       processed independently and never discarded as duplicates.</li>
     *   <li>If {@code eventId} is absent (fallback), we compare both {@code currentStatus}
     *       AND {@code timestamp} to avoid dropping legitimate re-attempt transitions,
     *       defaulting to processing rather than silent discarding.</li>
     * </ul>
     */
    private boolean isDuplicateEvent(ParcelStatusUpdatedEvent event) {
        if (event.getParcelId() == null) {
            return false;
        }
        try {
            List<WebhookDeliveryLog> existingLogs = logRepository.findByParcelId(event.getParcelId());
            if (existingLogs == null || existingLogs.isEmpty()) {
                return false;
            }

            UUID eventId = event.getEventId();
            String eventIdStr = eventId != null ? eventId.toString() : null;
            String currentStatus = event.getCurrentStatus();
            String timestampStr = event.getTimestamp() != null ? event.getTimestamp().toString() : null;

            for (WebhookDeliveryLog deliveryLog : existingLogs) {
                if (deliveryLog.getDeliveryStatus() == WebhookDeliveryStatus.DELIVERED
                        || deliveryLog.getDeliveryStatus() == WebhookDeliveryStatus.PENDING
                        || deliveryLog.getDeliveryStatus() == WebhookDeliveryStatus.RETRYING) {
                    String payload = deliveryLog.getPayload();
                    if (payload == null) {
                        continue;
                    }

                    if (eventIdStr != null) {
                        // Canonical primary identity check
                        if (payload.contains(eventIdStr)) {
                            return true;
                        }
                    } else if (currentStatus != null && timestampStr != null) {
                        // Conservative fallback: only duplicate if both status AND timestamp match
                        if (payload.contains("\"currentStatus\":\"" + currentStatus + "\"")
                                && payload.contains(timestampStr)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Non-fatal: Error checking for duplicate delivery log for parcelId={}: {}",
                    event.getParcelId(), PiiMaskingUtil.maskText(ex.getMessage()));
        }
        return false;
    }
}
