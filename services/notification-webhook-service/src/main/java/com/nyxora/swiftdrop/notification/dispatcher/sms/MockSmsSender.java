package com.nyxora.swiftdrop.notification.dispatcher.sms;

import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Mock SMS Dispatcher for simulating cellular SMS gateway transmission.
 *
 * Responsibilities:
 * - Simulates outbound SMS notification delivery to parcel recipients.
 * - Enforces privacy protection by masking recipient phone numbers in logs via PiiMaskingUtil.
 * - Supports runtime activation/deactivation via configuration property 'app.notification.sms.mock-enabled'.
 * - Provides defensive input validation to guard against null or blank phone numbers and message bodies.
 */
@Service
@Slf4j
public class MockSmsSender {

    @Value("${app.notification.sms.mock-enabled:true}")
    private boolean mockEnabled;

    /**
     * Simulates sending an SMS message to the specified recipient phone number.
     *
     * @param recipientPhone The recipient's cellular telephone number
     * @param message The SMS text payload
     * @return true if simulated dispatch succeeded or was safely processed; false if discarded due to invalid input or disabled flag
     */
    public boolean sendSms(String recipientPhone, String message) {
        if (!mockEnabled) {
            log.info("Mock SMS sender is disabled via configuration. Skipping SMS transmission.");
            return false;
        }

        if (recipientPhone == null || recipientPhone.isBlank()) {
            log.warn("Cannot dispatch SMS: recipientPhone is null or empty.");
            return false;
        }

        if (message == null || message.isBlank()) {
            log.warn("Cannot dispatch SMS: message content is null or empty for recipient: {}",
                    PiiMaskingUtil.maskPhoneNumber(recipientPhone));
            return false;
        }

        String maskedPhone = PiiMaskingUtil.maskPhoneNumber(recipientPhone);

        log.info("[MOCK SMS GATEWAY] Message dispatched successfully: recipient={}, length={} chars, text='{}'",
                maskedPhone,
                message.length(),
                message);

        return true;
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }
}

