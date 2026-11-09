package com.nyxora.swiftdrop.ai_assistant_service.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateQuoteToolTest {

    private RateQuoteTool rateQuoteTool;

    @BeforeEach
    void setUp() {
        rateQuoteTool = new RateQuoteTool();
    }

    @Test
    @DisplayName("Should return INVALID_INPUT when weight is invalid")
    void shouldReturnInvalidInputForInvalidWeight() {
        RateQuoteResult nullWeight = rateQuoteTool.calculateRateQuote(null, "Colombo", "Kandy", "MEDIUM");
        assertThat(nullWeight.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(nullWeight.message()).contains("positive number");

        RateQuoteResult zeroWeight = rateQuoteTool.calculateRateQuote(0.0, "Colombo", "Kandy", "MEDIUM");
        assertThat(zeroWeight.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);

        RateQuoteResult negativeWeight = rateQuoteTool.calculateRateQuote(-2.5, "Colombo", "Kandy", "MEDIUM");
        assertThat(negativeWeight.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
    }

    @Test
    @DisplayName("Should return INVALID_INPUT when origin or destination city is blank")
    void shouldReturnInvalidInputForBlankCities() {
        RateQuoteResult blankOrigin = rateQuoteTool.calculateRateQuote(1.5, "   ", "Kandy", "SMALL");
        assertThat(blankOrigin.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(blankOrigin.message()).contains("Origin city is required");

        RateQuoteResult blankDest = rateQuoteTool.calculateRateQuote(1.5, "Colombo", "", "SMALL");
        assertThat(blankDest.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(blankDest.message()).contains("Destination city is required");
    }

    @Test
    @DisplayName("Should return INVALID_INPUT when parcel size is blank")
    void shouldReturnInvalidInputForBlankParcelSize() {
        RateQuoteResult blankSize = rateQuoteTool.calculateRateQuote(1.5, "Colombo", "Kandy", null);
        assertThat(blankSize.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(blankSize.message()).contains("Parcel size");
    }

    @Test
    @DisplayName("Should return UNAVAILABLE when live pricing backend is not connected")
    void shouldReturnUnavailableForValidInput() {
        RateQuoteResult result = rateQuoteTool.calculateRateQuote(3.5, "Colombo", "Galle", "LARGE");
        assertThat(result.status()).isEqualTo(ToolResultStatus.UNAVAILABLE);
        assertThat(result.message()).contains("temporarily unavailable");
    }
}

