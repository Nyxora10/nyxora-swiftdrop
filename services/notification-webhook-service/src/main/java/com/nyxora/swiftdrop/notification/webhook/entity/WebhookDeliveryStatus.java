package com.nyxora.swiftdrop.notification.webhook.entity;

/**
 * Enumeration of webhook delivery lifecycle states.
 *
 * <p>Each outbound merchant webhook progresses through these states
 * as the dispatch engine attempts delivery and, if necessary, schedules
 * exponential-backoff retries via the Dead-Letter Exchange.</p>
 *
 * <p>Stored as a VARCHAR(30) string in the {@code webhook_delivery_logs.delivery_status}
 * column of {@code notification_db}.</p>
 */
public enum WebhookDeliveryStatus {

    /**
     * Initial state before the first delivery attempt has been made.
     */
    PENDING,

    /**
     * Webhook payload was successfully delivered to the merchant endpoint (HTTP 2xx response).
     */
    DELIVERED,

    /**
     * Delivery failed with a transient error (HTTP 5xx or network timeout)
     * and another retry attempt has been scheduled via the DLQ.
     */
    RETRYING,

    /**
     * The initial delivery attempt and all five permitted retries have failed.
     * The log is preserved for administrative audit review and manual re-dispatch.
     */
    DEAD_LETTER
}

