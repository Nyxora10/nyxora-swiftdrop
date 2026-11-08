package com.nyxora.swiftdrop.parcel.pricing.strategy;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.PricingRule;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.pricing.ParcelQuoteRequest;
import com.nyxora.swiftdrop.parcel.pricing.ParcelQuoteResponse;
import com.nyxora.swiftdrop.parcel.pricing.QuoteTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link StandardZonePricingStrategy}.
 * Validates standard zone-based fee calculation, boundary conditions,
 * and strategy discrimination.
 */
@DisplayName("StandardZonePricingStrategy Tests")
class StandardZonePricingStrategyTest {

    private StandardZonePricingStrategy strategy;
    private DeliveryZone destZone;
    private PricingRule rule;

    @BeforeEach
    void setUp() {
        strategy = new StandardZonePricingStrategy();

        destZone = DeliveryZone.builder()
                .id(2)
                .zoneCode("SB-GAL-01")
                .zoneName("Galle City")
                .district("Galle")
                .baseSurcharge(new BigDecimal("50.00"))
                .isActive(true)
                .build();

        rule = PricingRule.builder()
                .id(1)
                .baseFee(new BigDecimal("150.00"))
                .ratePerKg(new BigDecimal("25.00"))
                .parcelSize(ParcelSize.SMALL)
                .sizeMultiplier(new BigDecimal("1.00"))
                .build();
    }

    @Test
    @DisplayName("supports() returns true for STANDARD tier")
    void supports_standardTier_returnsTrue() {
        assertThat(strategy.supports(QuoteTier.STANDARD)).isTrue();
    }

    @Test
    @DisplayName("supports() returns false for non-STANDARD tiers")
    void supports_otherTiers_returnsFalse() {
        assertThat(strategy.supports(QuoteTier.MERCHANT)).isFalse();
        assertThat(strategy.supports(QuoteTier.EXPRESS)).isFalse();
    }

    @Test
    @DisplayName("Standard formula: (baseFee + weight*rate) * multiplier + surcharge")
    void calculateFee_standardFormula_correctTotal() {
        // (150 + 2.0*25) * 1.0 + 50 = (150 + 50) * 1.0 + 50 = 250.00
        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .weightKg(new BigDecimal("2.0"))
                .parcelSize(ParcelSize.SMALL)
                .tier(QuoteTier.STANDARD)
                .build();

        ParcelQuoteResponse response = strategy.calculateFee(request, rule, destZone);

        assertThat(response.getTotalFee()).isEqualByComparingTo("250.00");
        assertThat(response.getAppliedTier()).isEqualTo(QuoteTier.STANDARD);
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(response.getExpressSurcharge()).isEqualByComparingTo("0.00");
        assertThat(response.getWeightCharge()).isEqualByComparingTo("50.00");
        assertThat(response.getZoneSurcharge()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Size multiplier is correctly applied (LARGE = 1.5x)")
    void calculateFee_withSizeMultiplier_appliesCorrectly() {
        rule = PricingRule.builder()
                .baseFee(new BigDecimal("100.00"))
                .ratePerKg(new BigDecimal("20.00"))
                .parcelSize(ParcelSize.LARGE)
                .sizeMultiplier(new BigDecimal("1.50"))
                .build();

        // (100 + 3*20) * 1.5 + 50 = 160 * 1.5 + 50 = 240 + 50 = 290.00
        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1).destinationZoneId(2)
                .weightKg(new BigDecimal("3.0"))
                .parcelSize(ParcelSize.LARGE)
                .tier(QuoteTier.STANDARD).build();

        ParcelQuoteResponse response = strategy.calculateFee(request, rule, destZone);

        assertThat(response.getTotalFee()).isEqualByComparingTo("290.00");
    }

    @Test
    @DisplayName("Minimum weight 0.01 kg calculates correctly")
    void calculateFee_minimumWeight_calculatesCorrectly() {
        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1).destinationZoneId(2)
                .weightKg(new BigDecimal("0.01"))
                .parcelSize(ParcelSize.SMALL)
                .tier(QuoteTier.STANDARD).build();

        // (150 + 0.01*25) * 1.0 + 50 = 150.25 + 50 = 200.25
        ParcelQuoteResponse response = strategy.calculateFee(request, rule, destZone);
        assertThat(response.getTotalFee()).isEqualByComparingTo("200.25");
    }

    @Test
    @DisplayName("Zero zone surcharge does not inflate fee")
    void calculateFee_zeroSurcharge_noInflation() {
        destZone.setBaseSurcharge(BigDecimal.ZERO);

        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1).destinationZoneId(2)
                .weightKg(new BigDecimal("1.0"))
                .parcelSize(ParcelSize.SMALL)
                .tier(QuoteTier.STANDARD).build();

        // (150 + 1*25) * 1.0 + 0 = 175.00
        ParcelQuoteResponse response = strategy.calculateFee(request, rule, destZone);
        assertThat(response.getTotalFee()).isEqualByComparingTo("175.00");
    }
}

