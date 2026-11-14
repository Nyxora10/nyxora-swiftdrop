package com.nyxora.swiftdrop.notification.webhook.client;

import com.nyxora.swiftdrop.notification.webhook.client.dto.MerchantWebhookConfigResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantWebhookConfigResponseTest {

    private final UUID merchantId = UUID.randomUUID();

    @Test
    void acceptsCompleteActiveConfiguration() {
        assertThat(new MerchantWebhookConfigResponse(merchantId, "https://example.com/hook", "secret", true)
                .isEligibleForDispatch()).isTrue();
    }

    @Test
    void rejectsInactiveConfiguration() {
        assertThat(new MerchantWebhookConfigResponse(merchantId, "https://example.com/hook", "secret", false)
                .isEligibleForDispatch()).isFalse();
        assertThat(new MerchantWebhookConfigResponse(merchantId, "https://example.com/hook", "secret", null)
                .isEligibleForDispatch()).isFalse();
    }

    @Test
    void rejectsMissingOrBlankUrlAndSecret() {
        assertThat(new MerchantWebhookConfigResponse(merchantId, null, "secret", true)
                .isEligibleForDispatch()).isFalse();
        assertThat(new MerchantWebhookConfigResponse(merchantId, " ", "secret", true)
                .isEligibleForDispatch()).isFalse();
        assertThat(new MerchantWebhookConfigResponse(merchantId, "https://example.com", null, true)
                .isEligibleForDispatch()).isFalse();
        assertThat(new MerchantWebhookConfigResponse(merchantId, "https://example.com", " ", true)
                .isEligibleForDispatch()).isFalse();
    }
}
