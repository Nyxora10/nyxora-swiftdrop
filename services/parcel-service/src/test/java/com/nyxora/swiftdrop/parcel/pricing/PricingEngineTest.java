package com.nyxora.swiftdrop.parcel.pricing;

import com.nyxora.swiftdrop.parcel.domain.entity.DefaultPricingRule;
import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.PricingRule;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.RouteType;
import com.nyxora.swiftdrop.parcel.domain.repository.DefaultPricingRuleRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.PricingRuleRepository;
import com.nyxora.swiftdrop.parcel.pricing.strategy.ExpressSameDayPricingStrategy;
import com.nyxora.swiftdrop.parcel.pricing.strategy.MerchantDiscountPricingStrategy;
import com.nyxora.swiftdrop.parcel.pricing.strategy.StandardZonePricingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PricingEngine Orchestrator Unit Tests")
class PricingEngineTest {

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @Mock
    private DefaultPricingRuleRepository defaultPricingRuleRepository;

    @Mock
    private DeliveryZoneRepository deliveryZoneRepository;

    private PricingEngine pricingEngine;

    private DeliveryZone originZone;
    private DeliveryZone destZone;
    private PricingRule pricingRule;

    @BeforeEach
    void setUp() {
        List<PricingStrategy> strategies = List.of(
                new StandardZonePricingStrategy(),
                new MerchantDiscountPricingStrategy(),
                new ExpressSameDayPricingStrategy()
        );
        pricingEngine = new PricingEngine(
                strategies,
                pricingRuleRepository,
                defaultPricingRuleRepository,
                deliveryZoneRepository);

        originZone = DeliveryZone.builder()
                .id(1)
                .zoneCode("WP-COL-01")
                .zoneName("Colombo Central")
                .district("Colombo")
                .baseSurcharge(BigDecimal.ZERO)
                .isActive(true)
                .build();

        destZone = DeliveryZone.builder()
                .id(2)
                .zoneCode("CP-KAN-01")
                .zoneName("Kandy City")
                .district("Kandy")
                .baseSurcharge(new BigDecimal("75.00"))
                .isActive(true)
                .build();

        pricingRule = PricingRule.builder()
                .id(10)
                .originZone(originZone)
                .destinationZone(destZone)
                .baseFee(new BigDecimal("350.00"))
                .ratePerKg(new BigDecimal("50.00"))
                .parcelSize(ParcelSize.MEDIUM)
                .sizeMultiplier(new BigDecimal("1.20"))
                .build();
    }

    @Test
    @DisplayName("Successfully calculates quote for STANDARD tier")
    void calculateQuote_standardTier_success() {
        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(originZone));
        when(deliveryZoneRepository.findById(2)).thenReturn(Optional.of(destZone));
        when(pricingRuleRepository.findByOriginZone_IdAndDestinationZone_IdAndParcelSize(1, 2, ParcelSize.MEDIUM))
                .thenReturn(Optional.of(pricingRule));

        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .weightKg(new BigDecimal("2.5"))
                .parcelSize(ParcelSize.MEDIUM)
                .tier(QuoteTier.STANDARD)
                .build();

        ParcelQuoteResponse quote = pricingEngine.calculateQuote(request);

        assertThat(quote).isNotNull();
        assertThat(quote.getAppliedTier()).isEqualTo(QuoteTier.STANDARD);
        // (350 + 2.5 * 50) * 1.20 + 75 = (350 + 125) * 1.20 + 75 = 475 * 1.20 + 75 = 570 + 75 = 645.00
        assertThat(quote.getTotalFee()).isEqualByComparingTo("645.00");
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when destination zone is not found")
    void calculateQuote_destZoneNotFound_throwsException() {
        when(deliveryZoneRepository.findById(99)).thenReturn(Optional.empty());

        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1)
                .destinationZoneId(99)
                .weightKg(new BigDecimal("1.0"))
                .parcelSize(ParcelSize.SMALL)
                .tier(QuoteTier.STANDARD)
                .build();

        assertThatThrownBy(() -> pricingEngine.calculateQuote(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Destination zone not found: 99");
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when destination zone is inactive")
    void calculateQuote_destZoneInactive_throwsException() {
        destZone.setIsActive(false);
        when(deliveryZoneRepository.findById(2)).thenReturn(Optional.of(destZone));

        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .weightKg(new BigDecimal("1.0"))
                .parcelSize(ParcelSize.SMALL)
                .tier(QuoteTier.STANDARD)
                .build();

        assertThatThrownBy(() -> pricingEngine.calculateQuote(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Destination zone is not active");
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when origin zone is inactive")
    void calculateQuote_originZoneInactive_throwsException() {
        originZone.setIsActive(false);
        when(deliveryZoneRepository.findById(2)).thenReturn(Optional.of(destZone));
        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(originZone));

        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .weightKg(new BigDecimal("1.0"))
                .parcelSize(ParcelSize.SMALL)
                .tier(QuoteTier.STANDARD)
                .build();

        assertThatThrownBy(() -> pricingEngine.calculateQuote(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Origin zone is not active");
    }

    @Test
    @DisplayName("Uses a different-zone default when no exact route override exists")
    void calculateQuote_noOverride_usesDefaultRule() {
        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(originZone));
        when(deliveryZoneRepository.findById(2)).thenReturn(Optional.of(destZone));
        when(pricingRuleRepository.findByOriginZone_IdAndDestinationZone_IdAndParcelSize(1, 2, ParcelSize.LARGE))
                .thenReturn(Optional.empty());
        when(defaultPricingRuleRepository.findByRouteTypeAndParcelSize(RouteType.DIFFERENT_ZONE, ParcelSize.LARGE))
                .thenReturn(Optional.of(defaultRule(RouteType.DIFFERENT_ZONE, ParcelSize.LARGE,
                        "450.00", "80.00", "1.50")));

        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .weightKg(new BigDecimal("10.0"))
                .parcelSize(ParcelSize.LARGE)
                .tier(QuoteTier.STANDARD)
                .build();

        ParcelQuoteResponse quote = pricingEngine.calculateQuote(request);

        assertThat(quote.getBaseFee()).isEqualByComparingTo("450.00");
        assertThat(quote.getSizeMultiplier()).isEqualByComparingTo("1.50");
        assertThat(quote.getTotalFee()).isEqualByComparingTo("1950.00");
    }

    @Test
    @DisplayName("Uses a same-zone default for delivery within one district")
    void calculateQuote_sameZone_usesDefaultRule() {
        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(originZone));
        when(pricingRuleRepository.findByOriginZone_IdAndDestinationZone_IdAndParcelSize(1, 1, ParcelSize.SMALL))
                .thenReturn(Optional.empty());
        when(defaultPricingRuleRepository.findByRouteTypeAndParcelSize(RouteType.SAME_ZONE, ParcelSize.SMALL))
                .thenReturn(Optional.of(defaultRule(RouteType.SAME_ZONE, ParcelSize.SMALL,
                        "350.00", "80.00", "1.00")));

        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1)
                .destinationZoneId(1)
                .weightKg(new BigDecimal("1.0"))
                .parcelSize(ParcelSize.SMALL)
                .tier(QuoteTier.STANDARD)
                .build();

        ParcelQuoteResponse quote = pricingEngine.calculateQuote(request);

        assertThat(quote.getBaseFee()).isEqualByComparingTo("350.00");
        assertThat(quote.getTotalFee()).isEqualByComparingTo("430.00");
    }

    @Test
    @DisplayName("Fails clearly when neither an override nor a required default exists")
    void calculateQuote_defaultMissing_throwsException() {
        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(originZone));
        when(deliveryZoneRepository.findById(2)).thenReturn(Optional.of(destZone));
        when(pricingRuleRepository.findByOriginZone_IdAndDestinationZone_IdAndParcelSize(1, 2, ParcelSize.SMALL))
                .thenReturn(Optional.empty());
        when(defaultPricingRuleRepository.findByRouteTypeAndParcelSize(RouteType.DIFFERENT_ZONE, ParcelSize.SMALL))
                .thenReturn(Optional.empty());

        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .weightKg(BigDecimal.ONE)
                .parcelSize(ParcelSize.SMALL)
                .tier(QuoteTier.STANDARD)
                .build();

        assertThatThrownBy(() -> pricingEngine.calculateQuote(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No default pricing rule found");
    }

    private DefaultPricingRule defaultRule(
            RouteType routeType,
            ParcelSize parcelSize,
            String baseFee,
            String ratePerKg,
            String sizeMultiplier
    ) {
        return DefaultPricingRule.builder()
                .routeType(routeType)
                .parcelSize(parcelSize)
                .baseFee(new BigDecimal(baseFee))
                .ratePerKg(new BigDecimal(ratePerKg))
                .sizeMultiplier(new BigDecimal(sizeMultiplier))
                .build();
    }
}
