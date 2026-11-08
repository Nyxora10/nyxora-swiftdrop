package com.nyxora.swiftdrop.notification.webhook.retry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebhookRetryPolicy Unit Tests")
class WebhookRetryPolicyTest {

    @Test
    @DisplayName("Should return exact 5-tier delay durations for attempts 0 through 4")
    void shouldReturnCorrectDelaysForEveryTier() {
        // currentCount = 0 -> Retry 1 (10 seconds)
        assertEquals(Duration.ofSeconds(10), WebhookRetryPolicy.getDelayForNextRetry(0));
        assertEquals(10_000L, WebhookRetryPolicy.getDelayMillisForNextRetry(0));
        assertEquals("webhook.retry.10s", WebhookRetryPolicy.getRoutingKeyForNextRetry(0));

        // currentCount = 1 -> Retry 2 (30 seconds)
        assertEquals(Duration.ofSeconds(30), WebhookRetryPolicy.getDelayForNextRetry(1));
        assertEquals(30_000L, WebhookRetryPolicy.getDelayMillisForNextRetry(1));
        assertEquals("webhook.retry.30s", WebhookRetryPolicy.getRoutingKeyForNextRetry(1));

        // currentCount = 2 -> Retry 3 (2 minutes / 120s)
        assertEquals(Duration.ofMinutes(2), WebhookRetryPolicy.getDelayForNextRetry(2));
        assertEquals(120_000L, WebhookRetryPolicy.getDelayMillisForNextRetry(2));
        assertEquals("webhook.retry.2m", WebhookRetryPolicy.getRoutingKeyForNextRetry(2));

        // currentCount = 3 -> Retry 4 (10 minutes / 600s)
        assertEquals(Duration.ofMinutes(10), WebhookRetryPolicy.getDelayForNextRetry(3));
        assertEquals(600_000L, WebhookRetryPolicy.getDelayMillisForNextRetry(3));
        assertEquals("webhook.retry.10m", WebhookRetryPolicy.getRoutingKeyForNextRetry(3));

        // currentCount = 4 -> Retry 5 (30 minutes / 1800s)
        assertEquals(Duration.ofMinutes(30), WebhookRetryPolicy.getDelayForNextRetry(4));
        assertEquals(1_800_000L, WebhookRetryPolicy.getDelayMillisForNextRetry(4));
        assertEquals("webhook.retry.30m", WebhookRetryPolicy.getRoutingKeyForNextRetry(4));
    }

    @Test
    @DisplayName("Should allow retries strictly for counts 0 through 4")
    void shouldValidateCanRetryBoundaries() {
        // Initial failure at count 0 allows retry 1
        assertTrue(WebhookRetryPolicy.canRetry(0));
        assertTrue(WebhookRetryPolicy.canRetry(1));
        assertTrue(WebhookRetryPolicy.canRetry(2));
        assertTrue(WebhookRetryPolicy.canRetry(3));
        // Failure at count 4 allows final retry 5
        assertTrue(WebhookRetryPolicy.canRetry(4));

        // Failure at count 5 means all 5 retries have been exhausted
        assertFalse(WebhookRetryPolicy.canRetry(5));
        assertFalse(WebhookRetryPolicy.canRetry(6));
        assertFalse(WebhookRetryPolicy.canRetry(100));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -5, -99})
    @DisplayName("Should reject negative retry counts")
    void shouldRejectNegativeRetryCounts(int invalidCount) {
        assertFalse(WebhookRetryPolicy.canRetry(invalidCount));
        assertThrows(IllegalArgumentException.class, () -> WebhookRetryPolicy.getDelayForNextRetry(invalidCount));
        assertThrows(IllegalArgumentException.class, () -> WebhookRetryPolicy.getRoutingKeyForNextRetry(invalidCount));
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6, 7, 50})
    @DisplayName("Should throw IllegalArgumentException when calculating delay for exhausted counts")
    void shouldThrowForExhaustedCounts(int exhaustedCount) {
        assertThrows(IllegalArgumentException.class, () -> WebhookRetryPolicy.getDelayForNextRetry(exhaustedCount));
        assertThrows(IllegalArgumentException.class, () -> WebhookRetryPolicy.getRoutingKeyForNextRetry(exhaustedCount));
    }

    @Test
    @DisplayName("Should calculate future nextRetryAt timestamp correctly")
    void shouldCalculateFutureTimestamp() {
        Instant before = Instant.now();
        Instant nextRetryAt = WebhookRetryPolicy.calculateNextRetryAt(0); // 10s delay
        Instant after = Instant.now();

        assertTrue(nextRetryAt.isAfter(before.plusSeconds(9)));
        assertTrue(nextRetryAt.isBefore(after.plusSeconds(11)));
    }

    @Test
    @DisplayName("Private constructor should throw UnsupportedOperationException")
    void privateConstructorShouldThrow() throws NoSuchMethodException {
        Constructor<WebhookRetryPolicy> constructor = WebhookRetryPolicy.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }
}

