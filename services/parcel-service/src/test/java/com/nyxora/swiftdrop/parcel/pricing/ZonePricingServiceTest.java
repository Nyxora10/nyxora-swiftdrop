package com.nyxora.swiftdrop.parcel.pricing;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.PricingRule;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.PricingRuleRepository;
import com.nyxora.swiftdrop.parcel.exception.ResourceConflictException;
import com.nyxora.swiftdrop.parcel.exception.ResourceNotFoundException;
import com.nyxora.swiftdrop.parcel.pricing.dto.CreatePricingRuleRequest;
import com.nyxora.swiftdrop.parcel.pricing.dto.CreateZoneRequest;
import com.nyxora.swiftdrop.parcel.pricing.dto.UpdatePricingRuleRequest;
import com.nyxora.swiftdrop.parcel.pricing.dto.UpdateZoneRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ZonePricingService Unit Tests")
class ZonePricingServiceTest {

    @Mock
    private DeliveryZoneRepository zoneRepository;

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @InjectMocks
    private ZonePricingService zonePricingService;

    private DeliveryZone zone1;
    private DeliveryZone zone2;
    private PricingRule rule1;

    @BeforeEach
    void setUp() {
        zone1 = DeliveryZone.builder()
                .id(1)
                .zoneCode("WP-COL-01")
                .zoneName("Colombo Fort")
                .district("Colombo")
                .baseSurcharge(BigDecimal.ZERO)
                .isActive(true)
                .build();

        zone2 = DeliveryZone.builder()
                .id(2)
                .zoneCode("WP-GAM-01")
                .zoneName("Gampaha Town")
                .district("Gampaha")
                .baseSurcharge(new BigDecimal("30.00"))
                .isActive(true)
                .build();

        rule1 = PricingRule.builder()
                .id(100)
                .originZone(zone1)
                .destinationZone(zone2)
                .baseFee(new BigDecimal("250.00"))
                .ratePerKg(new BigDecimal("35.00"))
                .parcelSize(ParcelSize.SMALL)
                .sizeMultiplier(BigDecimal.ONE)
                .build();
    }

    @Test
    @DisplayName("getAllZones returns all zones")
    void getAllZones_success() {
        when(zoneRepository.findAll()).thenReturn(List.of(zone1, zone2));

        List<DeliveryZone> result = zonePricingService.getAllZones();

        assertThat(result).hasSize(2);
        verify(zoneRepository).findAll();
    }

    @Test
    @DisplayName("getActiveZones returns only active zones")
    void getActiveZones_success() {
        when(zoneRepository.findByIsActiveTrue()).thenReturn(List.of(zone1));

        List<DeliveryZone> result = zonePricingService.getActiveZones();

        assertThat(result).hasSize(1);
        verify(zoneRepository).findByIsActiveTrue();
    }

    @Test
    @DisplayName("getZoneById throws ResourceNotFoundException for an unknown zone")
    void getZoneById_missing_throwsNotFound() {
        when(zoneRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> zonePricingService.getZoneById(999))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Delivery zone not found: 999");
    }

    @Test
    @DisplayName("createZone saves new zone when code is unique")
    void createZone_success() {
        CreateZoneRequest request = CreateZoneRequest.builder()
                .zoneCode("NP-JAF-01")
                .zoneName("Jaffna Central")
                .district("Jaffna")
                .baseSurcharge(new BigDecimal("120.00"))
                .build();

        when(zoneRepository.existsByZoneCode("NP-JAF-01")).thenReturn(false);
        when(zoneRepository.save(any(DeliveryZone.class))).thenAnswer(i -> {
            DeliveryZone z = i.getArgument(0);
            z.setId(3);
            return z;
        });

        DeliveryZone saved = zonePricingService.createZone(request);

        assertThat(saved.getId()).isEqualTo(3);
        assertThat(saved.getZoneCode()).isEqualTo("NP-JAF-01");
        assertThat(saved.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("createZone throws ResourceConflictException when zoneCode already exists")
    void createZone_duplicateCode_throwsException() {
        CreateZoneRequest request = CreateZoneRequest.builder()
                .zoneCode("WP-COL-01")
                .zoneName("Duplicate Colombo")
                .district("Colombo")
                .baseSurcharge(BigDecimal.ZERO)
                .build();

        when(zoneRepository.existsByZoneCode("WP-COL-01")).thenReturn(true);

        assertThatThrownBy(() -> zonePricingService.createZone(request))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("Zone code already exists: WP-COL-01");
    }

    @Test
    @DisplayName("updateZone updates provided fields")
    void updateZone_success() {
        when(zoneRepository.findById(1)).thenReturn(Optional.of(zone1));
        when(zoneRepository.save(any(DeliveryZone.class))).thenAnswer(i -> i.getArgument(0));

        UpdateZoneRequest update = UpdateZoneRequest.builder()
                .zoneName("Colombo Fort Updated")
                .baseSurcharge(new BigDecimal("15.00"))
                .isActive(false)
                .build();

        DeliveryZone result = zonePricingService.updateZone(1, update);

        assertThat(result.getZoneName()).isEqualTo("Colombo Fort Updated");
        assertThat(result.getBaseSurcharge()).isEqualByComparingTo("15.00");
        assertThat(result.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("updateZone updates district when other fields are null")
    void updateZone_districtOnly_success() {
        when(zoneRepository.findById(1)).thenReturn(Optional.of(zone1));
        when(zoneRepository.save(any(DeliveryZone.class))).thenAnswer(i -> i.getArgument(0));

        UpdateZoneRequest update = UpdateZoneRequest.builder()
                .district("Kalutara")
                .build();

        DeliveryZone result = zonePricingService.updateZone(1, update);

        assertThat(result.getDistrict()).isEqualTo("Kalutara");
        assertThat(result.getZoneName()).isEqualTo("Colombo Fort");
    }

    @Test
    @DisplayName("updateZone rejects an empty partial update")
    void updateZone_emptyPatch_throwsException() {
        when(zoneRepository.findById(1)).thenReturn(Optional.of(zone1));

        assertThatThrownBy(() -> zonePricingService.updateZone(1, UpdateZoneRequest.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one zone field must be provided for update");

        verify(zoneRepository, never()).save(any(DeliveryZone.class));
    }

    @Test
    @DisplayName("getPricingRuleById throws ResourceNotFoundException when rule ID does not exist")
    void getPricingRuleById_notFound_throwsException() {
        when(pricingRuleRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> zonePricingService.getPricingRuleById(999))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Pricing rule not found: 999");
    }

    @Test
    @DisplayName("getPricingRules with originZoneId filters rules by origin zone")
    void getPricingRules_withOriginZone_filters() {
        when(pricingRuleRepository.findByOriginZone_Id(1)).thenReturn(List.of(rule1));

        List<PricingRule> result = zonePricingService.getPricingRules(1);

        assertThat(result).hasSize(1);
        verify(pricingRuleRepository).findByOriginZone_Id(1);
    }

    @Test
    @DisplayName("getPricingRules without originZoneId returns all pricing rules")
    void getPricingRules_nullOriginZone_returnsAll() {
        when(pricingRuleRepository.findAll()).thenReturn(List.of(rule1));

        List<PricingRule> result = zonePricingService.getPricingRules(null);

        assertThat(result).hasSize(1);
        verify(pricingRuleRepository).findAll();
    }

    @Test
    @DisplayName("createPricingRule saves rule when zone pair and size are unique")
    void createPricingRule_success() {
        when(zoneRepository.findById(1)).thenReturn(Optional.of(zone1));
        when(zoneRepository.findById(2)).thenReturn(Optional.of(zone2));
        when(pricingRuleRepository.findByOriginZone_IdAndDestinationZone_IdAndParcelSize(1, 2, ParcelSize.SMALL))
                .thenReturn(Optional.empty());
        when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(i -> {
            PricingRule r = i.getArgument(0);
            r.setId(101);
            return r;
        });

        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .baseFee(new BigDecimal("200.00"))
                .ratePerKg(new BigDecimal("30.00"))
                .parcelSize(ParcelSize.SMALL)
                .sizeMultiplier(BigDecimal.ONE)
                .build();

        PricingRule saved = zonePricingService.createPricingRule(request);

        assertThat(saved.getId()).isEqualTo(101);
        assertThat(saved.getBaseFee()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("createPricingRule throws ResourceConflictException when duplicate rule exists")
    void createPricingRule_duplicateRule_throwsException() {
        when(zoneRepository.findById(1)).thenReturn(Optional.of(zone1));
        when(zoneRepository.findById(2)).thenReturn(Optional.of(zone2));
        when(pricingRuleRepository.findByOriginZone_IdAndDestinationZone_IdAndParcelSize(1, 2, ParcelSize.SMALL))
                .thenReturn(Optional.of(rule1));

        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .baseFee(new BigDecimal("200.00"))
                .ratePerKg(new BigDecimal("30.00"))
                .parcelSize(ParcelSize.SMALL)
                .sizeMultiplier(BigDecimal.ONE)
                .build();

        assertThatThrownBy(() -> zonePricingService.createPricingRule(request))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("Pricing rule already exists");
    }

    @Test
    @DisplayName("createPricingRule rejects a rule from a zone to itself")
    void createPricingRule_sameZone_throwsException() {
        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .originZoneId(1)
                .destinationZoneId(1)
                .baseFee(new BigDecimal("200.00"))
                .ratePerKg(new BigDecimal("30.00"))
                .parcelSize(ParcelSize.SMALL)
                .sizeMultiplier(BigDecimal.ONE)
                .build();

        assertThatThrownBy(() -> zonePricingService.createPricingRule(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Origin and destination zones must be different");

        verifyNoInteractions(zoneRepository, pricingRuleRepository);
    }

    @Test
    @DisplayName("createPricingRule throws ResourceNotFoundException when origin zone does not exist")
    void createPricingRule_originNotFound_throwsException() {
        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .originZoneId(99)
                .destinationZoneId(2)
                .baseFee(new BigDecimal("200.00"))
                .ratePerKg(new BigDecimal("30.00"))
                .parcelSize(ParcelSize.SMALL)
                .sizeMultiplier(BigDecimal.ONE)
                .build();

        when(zoneRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> zonePricingService.createPricingRule(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Origin zone not found: 99");
    }

    @Test
    @DisplayName("createPricingRule throws ResourceNotFoundException when destination zone does not exist")
    void createPricingRule_destinationNotFound_throwsException() {
        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .originZoneId(1)
                .destinationZoneId(99)
                .baseFee(new BigDecimal("200.00"))
                .ratePerKg(new BigDecimal("30.00"))
                .parcelSize(ParcelSize.SMALL)
                .sizeMultiplier(BigDecimal.ONE)
                .build();

        when(zoneRepository.findById(1)).thenReturn(Optional.of(zone1));
        when(zoneRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> zonePricingService.createPricingRule(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Destination zone not found: 99");
    }

    @Test
    @DisplayName("updatePricingRule modifies rule rates")
    void updatePricingRule_success() {
        when(pricingRuleRepository.findById(100)).thenReturn(Optional.of(rule1));
        when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(i -> i.getArgument(0));

        UpdatePricingRuleRequest update = UpdatePricingRuleRequest.builder()
                .baseFee(new BigDecimal("300.00"))
                .ratePerKg(new BigDecimal("45.00"))
                .build();

        PricingRule result = zonePricingService.updatePricingRule(100, update);

        assertThat(result.getBaseFee()).isEqualByComparingTo("300.00");
        assertThat(result.getRatePerKg()).isEqualByComparingTo("45.00");
    }

    @Test
    @DisplayName("updatePricingRule updates sizeMultiplier when other fields are null")
    void updatePricingRule_sizeMultiplierOnly_success() {
        when(pricingRuleRepository.findById(100)).thenReturn(Optional.of(rule1));
        when(pricingRuleRepository.save(any(PricingRule.class))).thenAnswer(i -> i.getArgument(0));

        UpdatePricingRuleRequest update = UpdatePricingRuleRequest.builder()
                .sizeMultiplier(new BigDecimal("1.35"))
                .build();

        PricingRule result = zonePricingService.updatePricingRule(100, update);

        assertThat(result.getSizeMultiplier()).isEqualByComparingTo("1.35");
        assertThat(result.getBaseFee()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("updatePricingRule rejects an empty partial update")
    void updatePricingRule_emptyPatch_throwsException() {
        when(pricingRuleRepository.findById(100)).thenReturn(Optional.of(rule1));

        assertThatThrownBy(() -> zonePricingService.updatePricingRule(100, UpdatePricingRuleRequest.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one pricing field must be provided for update");

        verify(pricingRuleRepository, never()).save(any(PricingRule.class));
    }
}
