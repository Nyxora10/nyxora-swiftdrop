package com.nyxora.swiftdrop.notification.webhook.exception;

/**
 * Thrown when a requested webhook delivery log does not exist or does not belong to the authenticated merchant.
 */
public class DeliveryLogNotFoundException extends RuntimeException {
    public DeliveryLogNotFoundException(String message) {
        super(message);
    }
}

