package com.nyxora.swiftdrop.notification.webhook.retry;

import com.nyxora.swiftdrop.notification.webhook.retry.dto.WebhookRetryMessage;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookRetryMessageTest {

    private final UUID merchantId = UUID.randomUUID();
    private final UUID parcelId = UUID.randomUUID();

    @Test
    void rejectsMissingDeliveryLogId() {
        assertThatThrownBy(() -> new WebhookRetryMessage(null, merchantId, parcelId, "event", "{}", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingMerchantId() {
        assertThatThrownBy(() -> new WebhookRetryMessage(UUID.randomUUID(), null, parcelId, "event", "{}", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankPayload() {
        assertThatThrownBy(() -> new WebhookRetryMessage(UUID.randomUUID(), merchantId, parcelId, "event", " ", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
