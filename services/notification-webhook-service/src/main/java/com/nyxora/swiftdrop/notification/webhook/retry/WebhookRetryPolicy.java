package com.nyxora.swiftdrop.notification.webhook.retry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Deterministic retry policy for outbound merchant webhooks (Task 3.4).
 *
 * <p>Enforces a 5-tier Dead-Letter Queue (DLQ) backoff schedule:
 * <ul>
 *   <li><b>Retry 1:</b> 10 seconds (after initial failure at retryCount = 0)</li>
 *   <li><b>Retry 2:</b> 30 seconds (after failure at retryCount = 1)</li>
 *   <li><b>Retry 3:</b> 2 minutes / 120 seconds (after failure at retryCount = 2)</li>
 *   <li><b>Retry 4:</b> 10 minutes / 600 seconds (after failure at retryCount = 3)</li>
 *   <li><b>Retry 5:</b> 30 minutes / 1800 seconds (after failure at retryCount = 4)</li>
 * </ul>
 *
 * <p>Semantics:
 * <ul>
 *   <li>{@code retryCount = 0} represents the initial dispatch attempt.</li>
 *   <li>{@code retryCount = 1..5} represents subsequent retry attempts.</li>
 *   <li>If an attempt fails when {@code retryCount == 5}, retries are exhausted and the
 *       delivery log transitions to {@code DEAD_LETTER}.</li>
 * </ul>
 */
public final class WebhookRetryPolicy {

    /**
     * Maximum number of permitted retry attempts following the initial delivery.
     */
    public static final int MAX_RETRIES = 5;

    /**
     * Immutable list of backoff intervals for retry attempts 1 through 5.
     */
    public static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofSeconds(10),     // Retry 1: 10 seconds
            Duration.ofSeconds(30),     // Retry 2: 30 seconds
            Duration.ofMinutes(2),      // Retry 3: 2 minutes (120s)
            Duration.ofMinutes(10),     // Retry 4: 10 minutes (600s)
            Duration.ofMinutes(30)      // Retry 5: 30 minutes (1800s)
    );

    /**
     * Routing key suffixes mapped 1:1 to each backoff tier.
     */
    public static final List<String> RETRY_ROUTING_KEYS = List.of(
            "webhook.retry.10s",
            "webhook.retry.30s",
            "webhook.retry.2m",
            "webhook.retry.10m",
            "webhook.retry.30m"
    );

    private WebhookRetryPolicy() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Determines whether another retry attempt is permitted based on the current retry count.
     *
     * @param currentRetryCount number of retries already performed (0 for initial failure)
     * @return {@code true} if {@code currentRetryCount < MAX_RETRIES}; {@code false} if exhausted or invalid
     */
    public static boolean canRetry(int currentRetryCount) {
        return currentRetryCount >= 0 && currentRetryCount < MAX_RETRIES;
    }

    /**
     * Returns the backoff delay duration for the next retry attempt.
     *
     * @param currentRetryCount current retry count (0 to 4)
     * @return {@link Duration} of delay for the upcoming attempt
     * @throws IllegalArgumentException if {@code currentRetryCount < 0} or {@code >= MAX_RETRIES}
     */
    public static Duration getDelayForNextRetry(int currentRetryCount) {
        if (!canRetry(currentRetryCount)) {
            throw new IllegalArgumentException(
                    "Cannot calculate next retry delay for retryCount=" + currentRetryCount
                            + ". Valid range is 0 to " + (MAX_RETRIES - 1) + ".");
        }
        return RETRY_DELAYS.get(currentRetryCount);
    }

    /**
     * Returns the backoff delay in milliseconds for the next retry attempt.
     *
     * @param currentRetryCount current retry count (0 to 4)
     * @return millisecond delay for RabbitMQ TTL configuration
     */
    public static long getDelayMillisForNextRetry(int currentRetryCount) {
        return getDelayForNextRetry(currentRetryCount).toMillis();
    }

    /**
     * Calculates the exact wall-clock timestamp at which the next retry attempt should execute.
     *
     * @param currentRetryCount current retry count (0 to 4)
     * @return target {@link Instant} for {@code nextRetryAt} persistence
     */
    public static Instant calculateNextRetryAt(int currentRetryCount) {
        return Instant.now().plus(getDelayForNextRetry(currentRetryCount));
    }

    /**
     * Resolves the AMQP routing key for the target TTL delay queue.
     *
     * @param currentRetryCount current retry count (0 to 4)
     * @return routing key string (e.g. {@code "webhook.retry.10s"})
     */
    public static String getRoutingKeyForNextRetry(int currentRetryCount) {
        if (!canRetry(currentRetryCount)) {
            throw new IllegalArgumentException(
                    "No routing key for retryCount=" + currentRetryCount
                            + ". Retries are exhausted or count is negative.");
        }
        return RETRY_ROUTING_KEYS.get(currentRetryCount);
    }
}

