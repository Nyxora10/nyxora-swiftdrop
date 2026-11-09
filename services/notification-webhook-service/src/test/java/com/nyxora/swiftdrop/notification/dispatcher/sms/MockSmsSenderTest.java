package com.nyxora.swiftdrop.notification.dispatcher.sms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockSmsSenderTest {

    private MockSmsSender mockSmsSender;

    @BeforeEach
    void setUp() {
        mockSmsSender = new MockSmsSender();
        ReflectionTestUtils.setField(mockSmsSender, "mockEnabled", true);
    }

    @Test
    @DisplayName("Should successfully dispatch SMS when enabled and inputs are valid")
    void shouldSendSmsSuccessfullyWhenInputsAreValid() {
        boolean result = mockSmsSender.sendSms("+94771234567", "Your SwiftDrop parcel is out for delivery!");
        assertTrue(result);
    }

    @Test
    @DisplayName("Should skip SMS dispatch and return false when mockEnabled is false")
    void shouldSkipSmsWhenMockDisabled() {
        mockSmsSender.setMockEnabled(false);
        assertFalse(mockSmsSender.isMockEnabled());

        boolean result = mockSmsSender.sendSms("+94771234567", "Test message");
        assertFalse(result);
    }

    @Test
    @DisplayName("Should return false when recipient phone is null")
    void shouldReturnFalseWhenPhoneIsNull() {
        boolean result = mockSmsSender.sendSms(null, "Test message");
        assertFalse(result);
    }

    @Test
    @DisplayName("Should return false when recipient phone is blank")
    void shouldReturnFalseWhenPhoneIsBlank() {
        boolean result = mockSmsSender.sendSms("   ", "Test message");
        assertFalse(result);
    }

    @Test
    @DisplayName("Should return false when message content is null")
    void shouldReturnFalseWhenMessageIsNull() {
        boolean result = mockSmsSender.sendSms("+94771234567", null);
        assertFalse(result);
    }

    @Test
    @DisplayName("Should return false when message content is blank")
    void shouldReturnFalseWhenMessageIsBlank() {
        boolean result = mockSmsSender.sendSms("+94771234567", "   ");
        assertFalse(result);
    }

    @Test
    @DisplayName("Should return true for domestic format phone number")
    void shouldReturnTrueForDomesticPhoneNumber() {
        boolean result = mockSmsSender.sendSms("0771234567", "Delivery notification test");
        assertTrue(result);
    }
}

