package com.nyxora.swiftdrop.parcel.pricing;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.pricing.dto.PublicDeliveryZoneResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Public, read-only query service for Sri Lankan logistics delivery zones.
 * Exclusively returns active delivery zones, applying sanitized search and district filters.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicZoneQueryService {

    private final DeliveryZoneRepository zoneRepository;

    /**
     * Retrieves active delivery zones matching optional search terms or district filters.
     * All searches are case-insensitive and trimmed of leading/trailing whitespace.
     *
     * @param search   optional free-text query (matches zone name, zone code, or district)
     * @param district optional administrative district filter
     * @return sanitized list of active delivery zone DTOs
     */
    @Transactional(readOnly = true)
    public List<PublicDeliveryZoneResponse> getActiveZones(String search, String district) {
        String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String cleanDistrict = (district != null && !district.trim().isEmpty()) ? district.trim() : null;

        List<DeliveryZone> zones;

        if (cleanSearch != null && cleanDistrict != null) {
            log.debug("Searching active zones in district [{}] with query [{}]", cleanDistrict, cleanSearch);
            zones = zoneRepository.searchActiveZonesInDistrict(cleanSearch, cleanDistrict);
        } else if (cleanSearch != null) {
            log.debug("Searching active zones with query [{}]", cleanSearch);
            zones = zoneRepository.searchActiveZones(cleanSearch);
        } else if (cleanDistrict != null) {
            log.debug("Filtering active zones in district [{}]", cleanDistrict);
            zones = zoneRepository.findByIsActiveTrueAndDistrictIgnoreCaseOrderByZoneNameAsc(cleanDistrict);
        } else {
            log.debug("Listing all active delivery zones ordered by district and name");
            zones = zoneRepository.findByIsActiveTrueOrderByDistrictAscZoneNameAsc();
        }

        return zones.stream()
                .map(this::mapToPublicResponse)
                .toList();
    }

    private PublicDeliveryZoneResponse mapToPublicResponse(DeliveryZone zone) {
        return PublicDeliveryZoneResponse.builder()
                .id(zone.getId())
                .zoneCode(zone.getZoneCode())
                .zoneName(zone.getZoneName())
                .district(zone.getDistrict())
                .build();
    }
}

