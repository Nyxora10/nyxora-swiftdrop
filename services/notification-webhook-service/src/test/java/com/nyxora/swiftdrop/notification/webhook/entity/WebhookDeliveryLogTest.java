package com.nyxora.swiftdrop.notification.webhook.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDeliveryLogTest {

    @Test
    void prePersistInitializesMissingDefaults() throws Exception {
        WebhookDeliveryLog log = new WebhookDeliveryLog();
        prePersist(log);
        assertThat(log.getCreatedAt()).isNotNull();
        assertThat(log.getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(log.getRetryCount()).isZero();
    }

    @Test
    void prePersistPreservesExistingValues() throws Exception {
        WebhookDeliveryLog log = new WebhookDeliveryLog();
        Instant createdAt = Instant.now();
        log.setCreatedAt(createdAt);
        log.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);
        log.setRetryCount(2);
        prePersist(log);
        assertThat(log.getCreatedAt()).isEqualTo(createdAt);
        assertThat(log.getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.RETRYING);
        assertThat(log.getRetryCount()).isEqualTo(2);
    }

    @Test
    void reportsRetryAvailabilityAtBoundary() {
        WebhookDeliveryLog log = new WebhookDeliveryLog();
        log.setRetryCount(4);
        assertThat(log.hasRetriesRemaining()).isTrue();
        log.setRetryCount(WebhookDeliveryLog.MAX_RETRY_COUNT);
        assertThat(log.hasRetriesRemaining()).isFalse();
    }

    private void prePersist(WebhookDeliveryLog log) throws Exception {
        Method method = WebhookDeliveryLog.class.getDeclaredMethod("onCreate");
        method.setAccessible(true);
        method.invoke(log);
    }
}
