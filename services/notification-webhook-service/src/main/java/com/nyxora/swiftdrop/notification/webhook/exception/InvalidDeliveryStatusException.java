package com.nyxora.swiftdrop.notification.webhook.exception;

import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;

/**
 * Thrown when an action is requested on a webhook delivery log whose current status is ineligible.
 */
public class InvalidDeliveryStatusException extends RuntimeException {

    private final WebhookDeliveryStatus currentStatus;

    public InvalidDeliveryStatusException(String message, WebhookDeliveryStatus currentStatus) {
        super(message);
        this.currentStatus = currentStatus;
    }

    public WebhookDeliveryStatus getCurrentStatus() {
        return currentStatus;
    }
}

