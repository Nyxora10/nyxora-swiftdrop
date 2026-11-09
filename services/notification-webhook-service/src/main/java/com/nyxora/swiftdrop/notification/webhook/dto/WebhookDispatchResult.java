package com.nyxora.swiftdrop.notification.webhook.dto;

import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;

/**
 * Encapsulates the outcome of an outbound webhook dispatch attempt.
 *
 * <p>Carries structured information regarding delivery success, HTTP status codes,
 * sanitized error classification, retry eligibility, and the persisted audit log.
 * Provides the decision context for the asynchronous RabbitMQ retry scheduler (Task 3.4).</p>
 *
 * @param success          {@code true} if the target endpoint responded with HTTP 2xx; {@code false} otherwise
 * @param httpStatusCode   HTTP response status code returned by the receiver, or {@code null} on network/client failure
 * @param errorCategory    Categorized failure code (e.g. AUTH_CONFIG_UNAVAILABLE, CONNECTION_TIMEOUT, HTTP_5XX, SSRF_BLOCKED)
 * @param errorMessage     Sanitized error description; guaranteed never to contain secrets or credentials
 * @param retryable        {@code true} if the delivery should be re-attempted via the DLQ backoff engine
 * @param deliveryLog      The persisted {@link WebhookDeliveryLog} representing this attempt
 */
public record WebhookDispatchResult(
        boolean success,
        Integer httpStatusCode,
        String errorCategory,
        String errorMessage,
        boolean retryable,
        WebhookDeliveryLog deliveryLog
) {

    public static WebhookDispatchResult successful(int statusCode, WebhookDeliveryLog log) {
        return new WebhookDispatchResult(
                true,
                statusCode,
                null,
                null,
                false,
                log
        );
    }

    public static WebhookDispatchResult retryableFailure(
            Integer statusCode,
            String errorCategory,
            String errorMessage,
            WebhookDeliveryLog log
    ) {
        return new WebhookDispatchResult(
                false,
                statusCode,
                errorCategory,
                errorMessage,
                true,
                log
        );
    }

    public static WebhookDispatchResult permanentFailure(
            Integer statusCode,
            String errorCategory,
            String errorMessage,
            WebhookDeliveryLog log
    ) {
        return new WebhookDispatchResult(
                false,
                statusCode,
                errorCategory,
                errorMessage,
                false,
                log
        );
    }
}

