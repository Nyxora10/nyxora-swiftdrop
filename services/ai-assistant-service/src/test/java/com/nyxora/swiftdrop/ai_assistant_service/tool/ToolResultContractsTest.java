package com.nyxora.swiftdrop.ai_assistant_service.tool;

import com.nyxora.swiftdrop.ai_assistant_service.dto.PublicDeliveryZoneResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultContractsTest {

    @Test
    @DisplayName("Should create RateQuoteResult using 7-arg success overload with custom and default currency")
    void shouldTestRateQuoteResultSuccessOverload() {
        RateQuoteResult resultWithCurrency = RateQuoteResult.success(
                "Colombo",
                "Kandy",
                2.5,
                "MEDIUM",
                new BigDecimal("500.00"),
                "USD",
                2
        );

        assertThat(resultWithCurrency.status()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(resultWithCurrency.currency()).isEqualTo("USD");
        assertThat(resultWithCurrency.estimatedDeliveryDays()).isEqualTo(2);

        RateQuoteResult resultDefaultCurrency = RateQuoteResult.success(
                "Colombo",
                "Kandy",
                2.5,
                "MEDIUM",
                new BigDecimal("500.00"),
                null,
                1
        );

        assertThat(resultDefaultCurrency.currency()).isEqualTo("LKR");
    }

    @Test
    @DisplayName("Should create RateQuoteResult unauthorized and unavailable instances")
    void shouldTestRateQuoteResultFactoryMethods() {
        RateQuoteResult unauth = RateQuoteResult.unauthorized("Unauthorized access");
        assertThat(unauth.status()).isEqualTo(ToolResultStatus.UNAUTHORIZED);
        assertThat(unauth.message()).isEqualTo("Unauthorized access");

        RateQuoteResult unavail = RateQuoteResult.unavailable("Service unavailable");
        assertThat(unavail.status()).isEqualTo(ToolResultStatus.UNAVAILABLE);

        RateQuoteResult invalid = RateQuoteResult.invalidInput("Bad input");
        assertThat(invalid.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);

        List<PublicDeliveryZoneResponse> zones = List.of(new PublicDeliveryZoneResponse(1, "WP-COL-01", "Colombo", "Colombo"));
        RateQuoteResult amb = RateQuoteResult.ambiguous("Ambiguous zone", "Colombo", "Kandy", zones);
        assertThat(amb.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(amb.availableZones()).hasSize(1);
    }

    @Test
    @DisplayName("Should create ParcelTrackingResult unauthorized instance")
    void shouldTestParcelTrackingResultUnauthorized() {
        ParcelTrackingResult result = ParcelTrackingResult.unauthorized("Access denied");

        assertThat(result.status()).isEqualTo(ToolResultStatus.UNAUTHORIZED);
        assertThat(result.message()).isEqualTo("Access denied");
        assertThat(result.trackingNumber()).isNull();
    }
}
