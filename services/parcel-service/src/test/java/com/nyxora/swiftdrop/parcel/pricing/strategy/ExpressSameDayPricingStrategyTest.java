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
 * Unit tests for {@link ExpressSameDayPricingStrategy}.
 * Validates the 1.5x expedited multiplier, express surcharge reporting,
 * and correct relationship to standard pricing.
 */
@DisplayName("ExpressSameDayPricingStrategy Tests")
class ExpressSameDayPricingStrategyTest {

    private ExpressSameDayPricingStrategy strategy;
    private DeliveryZone destZone;
    private PricingRule rule;

    @BeforeEach
    void setUp() {
        strategy = new ExpressSameDayPricingStrategy();

        destZone = DeliveryZone.builder()
                .id(2)
                .baseSurcharge(new BigDecimal("100.00"))
                .isActive(true)
                .build();

        rule = PricingRule.builder()
                .baseFee(new BigDecimal("200.00"))
                .ratePerKg(new BigDecimal("30.00"))
                .parcelSize(ParcelSize.MEDIUM)
                .sizeMultiplier(BigDecimal.ONE)
                .build();
    }

    @Test
    @DisplayName("supports() returns true for EXPRESS tier only")
    void supports_expressTier_returnsTrue() {
        assertThat(strategy.supports(QuoteTier.EXPRESS)).isTrue();
        assertThat(strategy.supports(QuoteTier.STANDARD)).isFalse();
        assertThat(strategy.supports(QuoteTier.MERCHANT)).isFalse();
    }

    @Test
    @DisplayName("Express fee = standard subtotal × 1.5 + zone surcharge")
    void calculateFee_expressFormula_correctTotal() {
        // weightCharge = 2 * 30 = 60
        // standardSubtotal = (200 + 60) * 1.0 = 260
        // expressTotal = 260 * 1.5 = 390
        // total = 390 + 100 = 490.00

        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1).destinationZoneId(2)
                .weightKg(new BigDecimal("2.0"))
                .parcelSize(ParcelSize.MEDIUM)
                .tier(QuoteTier.EXPRESS).build();

        ParcelQuoteResponse response = strategy.calculateFee(request, rule, destZone);

        assertThat(response.getTotalFee()).isEqualByComparingTo("490.00");
        assertThat(response.getAppliedTier()).isEqualTo(QuoteTier.EXPRESS);
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Express surcharge is the extra 50% above the standard subtotal")
    void calculateFee_expressSurchargeIsExtraFiftyPercent() {
        // standardSubtotal = (200 + 2*30) * 1.0 = 260
        // expressSurcharge = 260 * 0.5 = 130.00

        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1).destinationZoneId(2)
                .weightKg(new BigDecimal("2.0"))
                .parcelSize(ParcelSize.MEDIUM)
                .tier(QuoteTier.EXPRESS).build();

        ParcelQuoteResponse response = strategy.calculateFee(request, rule, destZone);

        assertThat(response.getExpressSurcharge()).isEqualByComparingTo("130.00");
    }

    @Test
    @DisplayName("Express total is always greater than standard total for same inputs")
    void calculateFee_expressTotalGreaterThanStandard() {
        StandardZonePricingStrategy standardStrategy = new StandardZonePricingStrategy();

        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1).destinationZoneId(2)
                .weightKg(new BigDecimal("5.0"))
                .parcelSize(ParcelSize.MEDIUM)
                .tier(QuoteTier.EXPRESS).build();

        ParcelQuoteResponse expressQuote = strategy.calculateFee(request, rule, destZone);

        ParcelQuoteRequest standardRequest = ParcelQuoteRequest.builder()
                .originZoneId(1).destinationZoneId(2)
                .weightKg(new BigDecimal("5.0"))
                .parcelSize(ParcelSize.MEDIUM)
                .tier(QuoteTier.STANDARD).build();
        ParcelQuoteResponse standardQuote = standardStrategy.calculateFee(standardRequest, rule, destZone);

        assertThat(expressQuote.getTotalFee()).isGreaterThan(standardQuote.getTotalFee());
    }

    @Test
    @DisplayName("Zone surcharge is added after express multiplier (not multiplied)")
    void calculateFee_zoneSurchargeAddedAfterMultiplier() {
        // With zero surcharge
        destZone.setBaseSurcharge(BigDecimal.ZERO);

        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1).destinationZoneId(2)
                .weightKg(new BigDecimal("2.0"))
                .parcelSize(ParcelSize.MEDIUM)
                .tier(QuoteTier.EXPRESS).build();

        ParcelQuoteResponse noSurcharge = strategy.calculateFee(request, rule, destZone);

        // With surcharge
        destZone.setBaseSurcharge(new BigDecimal("100.00"));
        ParcelQuoteResponse withSurcharge = strategy.calculateFee(request, rule, destZone);

        // The difference should be exactly the surcharge (not 1.5 * surcharge)
        BigDecimal diff = withSurcharge.getTotalFee().subtract(noSurcharge.getTotalFee());
        assertThat(diff).isEqualByComparingTo("100.00");
    }
}

