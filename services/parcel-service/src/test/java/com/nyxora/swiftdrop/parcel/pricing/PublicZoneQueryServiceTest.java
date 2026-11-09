package com.nyxora.swiftdrop.parcel.pricing;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.pricing.dto.PublicDeliveryZoneResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublicZoneQueryService Unit Tests")
class PublicZoneQueryServiceTest {

    @Mock
    private DeliveryZoneRepository zoneRepository;

    @InjectMocks
    private PublicZoneQueryService queryService;

    private DeliveryZone zoneColombo;
    private DeliveryZone zoneKandy;

    @BeforeEach
    void setUp() {
        zoneColombo = DeliveryZone.builder()
                .id(1)
                .zoneCode("WP-COL-01")
                .zoneName("Colombo Central Metro")
                .district("Colombo")
                .baseSurcharge(new BigDecimal("150.00"))
                .isActive(true)
                .build();

        zoneKandy = DeliveryZone.builder()
                .id(3)
                .zoneCode("CP-KAN-01")
                .zoneName("Kandy Central Highlands")
                .district("Kandy")
                .baseSurcharge(new BigDecimal("200.00"))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Returns all active zones when no query params provided")
    void getActiveZones_NoParams_ReturnsAllActiveZones() {
        when(zoneRepository.findByIsActiveTrueOrderByDistrictAscZoneNameAsc())
                .thenReturn(List.of(zoneColombo, zoneKandy));

        List<PublicDeliveryZoneResponse> result = queryService.getActiveZones(null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1);
        assertThat(result.get(0).getZoneCode()).isEqualTo("WP-COL-01");
        assertThat(result.get(0).getZoneName()).isEqualTo("Colombo Central Metro");
        assertThat(result.get(0).getDistrict()).isEqualTo("Colombo");
        verify(zoneRepository).findByIsActiveTrueOrderByDistrictAscZoneNameAsc();
    }

    @Test
    @DisplayName("Treats whitespace-only parameters as null and returns all active zones")
    void getActiveZones_WhitespaceParams_TreatedAsNull() {
        when(zoneRepository.findByIsActiveTrueOrderByDistrictAscZoneNameAsc())
                .thenReturn(List.of(zoneColombo));

        List<PublicDeliveryZoneResponse> result = queryService.getActiveZones("   ", "   ");

        assertThat(result).hasSize(1);
        verify(zoneRepository).findByIsActiveTrueOrderByDistrictAscZoneNameAsc();
    }

    @Test
    @DisplayName("Filters by district when only district is supplied")
    void getActiveZones_OnlyDistrict_CallsDistrictFinder() {
        when(zoneRepository.findByIsActiveTrueAndDistrictIgnoreCaseOrderByZoneNameAsc("Colombo"))
                .thenReturn(List.of(zoneColombo));

        List<PublicDeliveryZoneResponse> result = queryService.getActiveZones(null, "  Colombo  ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDistrict()).isEqualTo("Colombo");
        verify(zoneRepository).findByIsActiveTrueAndDistrictIgnoreCaseOrderByZoneNameAsc("Colombo");
    }

    @Test
    @DisplayName("Searches active zones across name, code, or district when only search is supplied")
    void getActiveZones_OnlySearch_CallsSearchFinder() {
        when(zoneRepository.searchActiveZones("metro"))
                .thenReturn(List.of(zoneColombo));

        List<PublicDeliveryZoneResponse> result = queryService.getActiveZones("  metro  ", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getZoneName()).isEqualTo("Colombo Central Metro");
        verify(zoneRepository).searchActiveZones("metro");
    }

    @Test
    @DisplayName("Filters by both search and district when both are provided")
    void getActiveZones_SearchAndDistrict_CallsCombinedFinder() {
        when(zoneRepository.searchActiveZonesInDistrict("Central", "Colombo"))
                .thenReturn(List.of(zoneColombo));

        List<PublicDeliveryZoneResponse> result = queryService.getActiveZones(" Central ", " Colombo ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1);
        verify(zoneRepository).searchActiveZonesInDistrict("Central", "Colombo");
    }

    @Test
    @DisplayName("Returns empty list when no active zones match criteria")
    void getActiveZones_NoMatches_ReturnsEmptyList() {
        when(zoneRepository.searchActiveZones("NonExistent"))
                .thenReturn(List.of());

        List<PublicDeliveryZoneResponse> result = queryService.getActiveZones("NonExistent", null);

        assertThat(result).isEmpty();
    }
}

