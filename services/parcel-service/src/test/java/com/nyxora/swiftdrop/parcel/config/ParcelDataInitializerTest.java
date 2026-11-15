package com.nyxora.swiftdrop.parcel.config;

import com.nyxora.swiftdrop.parcel.domain.entity.DefaultPricingRule;
import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.repository.DefaultPricingRuleRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParcelDataInitializerTest {

    @Mock
    private DeliveryZoneRepository deliveryZoneRepository;

    @Mock
    private DefaultPricingRuleRepository defaultPricingRuleRepository;

    @InjectMocks
    private ParcelDataInitializer initializer;

    @Test
    void runSeedsTwentyFiveDistrictHubsAndSixDefaultsWhenDatabaseIsEmpty() throws Exception {
        when(deliveryZoneRepository.findByZoneCode(any())).thenReturn(Optional.empty());
        when(defaultPricingRuleRepository.findByRouteTypeAndParcelSize(any(), any()))
                .thenReturn(Optional.empty());

        initializer.run();

        ArgumentCaptor<DeliveryZone> zones = ArgumentCaptor.forClass(DeliveryZone.class);
        verify(deliveryZoneRepository, times(25)).save(zones.capture());
        assertThat(zones.getAllValues())
                .extracting(DeliveryZone::getDistrict)
                .doesNotHaveDuplicates()
                .hasSize(25)
                .contains("Colombo", "Kandy", "Jaffna", "Trincomalee", "Monaragala");
        assertThat(zones.getAllValues())
                .allSatisfy(zone -> assertThat(zone.getHubLocation()).isNotBlank());

        ArgumentCaptor<DefaultPricingRule> defaults = ArgumentCaptor.forClass(DefaultPricingRule.class);
        verify(defaultPricingRuleRepository, times(6)).save(defaults.capture());
        assertThat(defaults.getAllValues())
                .extracting(rule -> rule.getRouteType() + ":" + rule.getParcelSize())
                .doesNotHaveDuplicates()
                .hasSize(6);
    }

    @Test
    void runPreservesExistingZoneAndDefaultValues() throws Exception {
        DeliveryZone existingZone = DeliveryZone.builder()
                .zoneCode("WP-COL-01")
                .hubLocation("Custom Colombo Operations Centre")
                .build();
        when(deliveryZoneRepository.findByZoneCode(any())).thenReturn(Optional.of(existingZone));
        when(defaultPricingRuleRepository.findByRouteTypeAndParcelSize(any(), any()))
                .thenReturn(Optional.of(DefaultPricingRule.builder().build()));

        initializer.run();

        verify(deliveryZoneRepository, never()).save(any());
        verify(defaultPricingRuleRepository, never()).save(any());
        assertThat(existingZone.getHubLocation()).isEqualTo("Custom Colombo Operations Centre");
    }
}
