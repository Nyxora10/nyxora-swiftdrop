package com.nyxora.swiftdrop.ai_assistant_service.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParcelTrackingToolTest {

    private ParcelTrackingTool parcelTrackingTool;

    @BeforeEach
    void setUp() {
        parcelTrackingTool = new ParcelTrackingTool();
    }

    @Test
    @DisplayName("Should return INVALID_INPUT when tracking number is null or blank")
    void shouldReturnInvalidInputForBlankTrackingNumber() {
        ParcelTrackingResult nullResult = parcelTrackingTool.getParcelTracking(null);
        assertThat(nullResult.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(nullResult.message()).contains("must not be empty");

        ParcelTrackingResult blankResult = parcelTrackingTool.getParcelTracking("   ");
        assertThat(blankResult.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(blankResult.message()).contains("must not be empty");
    }

    @Test
    @DisplayName("Should return INVALID_INPUT when tracking number format is invalid")
    void shouldReturnInvalidInputForMalformedTrackingNumber() {
        ParcelTrackingResult invalidResult = parcelTrackingTool.getParcelTracking("INVALID-123");
        assertThat(invalidResult.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(invalidResult.message()).contains("Invalid tracking number format");
    }

    @Test
    @DisplayName("Should return UNAVAILABLE when live tracking backend is not connected")
    void shouldReturnUnavailableForValidTrackingNumber() {
        ParcelTrackingResult result = parcelTrackingTool.getParcelTracking("SWIFT-98765");
        assertThat(result.status()).isEqualTo(ToolResultStatus.UNAVAILABLE);
        assertThat(result.message()).contains("temporarily unavailable");
    }
}

