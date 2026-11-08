package com.nyxora.swiftdrop.notification.webhook.exception;

/**
 * Thrown when a concurrent request is detected while attempting to transition a webhook delivery log for retry.
 */
public class ConcurrentRetryException extends RuntimeException {
    public ConcurrentRetryException(String message) {
        super(message);
    }
}

