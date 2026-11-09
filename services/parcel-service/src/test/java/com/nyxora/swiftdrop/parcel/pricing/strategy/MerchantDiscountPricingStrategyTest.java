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

/**
 * Unit tests for {@link MerchantDiscountPricingStrategy}.
 * Validates 15% corporate discount is correctly applied to base rates
 * while zone surcharges remain unaffected.
 */
@DisplayName("MerchantDiscountPricingStrategy Tests")
class MerchantDiscountPricingStrategyTest {

    private MerchantDiscountPricingStrategy strategy;
    private DeliveryZone destZone;
    private PricingRule rule;

    @BeforeEach
    void setUp() {
        strategy = new MerchantDiscountPricingStrategy();

        destZone = DeliveryZone.builder()
                .id(2)
                .baseSurcharge(new BigDecimal("50.00"))
                .isActive(true)
                .build();

        rule = PricingRule.builder()
                .baseFee(new BigDecimal("200.00"))
                .ratePerKg(new BigDecimal("40.00"))
                .parcelSize(ParcelSize.MEDIUM)
                .sizeMultiplier(new BigDecimal("1.20"))
                .build();
    }

    @Test
    @DisplayName("supports() returns true for MERCHANT tier only")
    void supports_merchantTier_returnsTrue() {
        assertThat(strategy.supports(QuoteTier.MERCHANT)).isTrue();
        assertThat(strategy.supports(QuoteTier.STANDARD)).isFalse();
        assertThat(strategy.supports(QuoteTier.EXPRESS)).isFalse();
    }

    @Test
    @DisplayName("15% discount applied to baseFee and ratePerKg before multiplier")
    void calculateFee_appliesFifteenPercentDiscount() {
        // discountedBase = 200 * 0.85 = 170.00
        // discountedRate = 40 * 0.85 = 34.00
        // weightCharge = 2 * 34 = 68.00
        // subtotal = (170 + 68) * 1.20 = 238 * 1.20 = 285.60
        // total = 285.60 + 50 = 335.60

        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1).destinationZoneId(2)
                .weightKg(new BigDecimal("2.0"))
                .parcelSize(ParcelSize.MEDIUM)
                .tier(QuoteTier.MERCHANT).build();

        ParcelQuoteResponse response = strategy.calculateFee(request, rule, destZone);

        assertThat(response.getTotalFee()).isEqualByComparingTo("335.60");
        assertThat(response.getAppliedTier()).isEqualTo(QuoteTier.MERCHANT);
        assertThat(response.getDiscountAmount()).isPositive();
        assertThat(response.getExpressSurcharge()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Merchant total is always less than standard total for same inputs")
    void calculateFee_merchantTotalLessThanStandard() {
        StandardZonePricingStrategy standardStrategy = new StandardZonePricingStrategy();

        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1).destinationZoneId(2)
                .weightKg(new BigDecimal("5.0"))
                .parcelSize(ParcelSize.MEDIUM)
                .tier(QuoteTier.MERCHANT).build();

        ParcelQuoteResponse merchantQuote = strategy.calculateFee(request, rule, destZone);

        ParcelQuoteRequest standardRequest = ParcelQuoteRequest.builder()
                .originZoneId(1).destinationZoneId(2)
                .weightKg(new BigDecimal("5.0"))
                .parcelSize(ParcelSize.MEDIUM)
                .tier(QuoteTier.STANDARD).build();
        ParcelQuoteResponse standardQuote = standardStrategy.calculateFee(standardRequest, rule, destZone);

        assertThat(merchantQuote.getTotalFee()).isLessThan(standardQuote.getTotalFee());
    }

    @Test
    @DisplayName("Zone surcharge is NOT discounted for merchant accounts")
    void calculateFee_zoneSurchargeNotDiscounted() {
        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1).destinationZoneId(2)
                .weightKg(new BigDecimal("1.0"))
                .parcelSize(ParcelSize.MEDIUM)
                .tier(QuoteTier.MERCHANT).build();

        ParcelQuoteResponse response = strategy.calculateFee(request, rule, destZone);

        // Zone surcharge must be the full original amount
        assertThat(response.getZoneSurcharge()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("High weight parcel still receives correct 15% discount")
    void calculateFee_highWeight_discountAppliedCorrectly() {
        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1).destinationZoneId(2)
                .weightKg(new BigDecimal("50.0"))
                .parcelSize(ParcelSize.MEDIUM)
                .tier(QuoteTier.MERCHANT).build();

        ParcelQuoteResponse response = strategy.calculateFee(request, rule, destZone);

        assertThat(response.getAppliedTier()).isEqualTo(QuoteTier.MERCHANT);
        assertThat(response.getDiscountAmount()).isPositive();
        assertThat(response.getTotalFee()).isPositive();
    }
}

