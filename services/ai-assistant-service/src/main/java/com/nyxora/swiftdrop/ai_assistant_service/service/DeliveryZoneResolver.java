package com.nyxora.swiftdrop.ai_assistant_service.service;

import com.nyxora.swiftdrop.ai_assistant_service.client.ParcelServiceClient;
import com.nyxora.swiftdrop.ai_assistant_service.dto.PublicDeliveryZoneResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Service responsible for resolving customer-provided location strings (cities,
 * districts, zone codes, or zone IDs) into validated Sri Lankan delivery zones.
 */
@Service
public class DeliveryZoneResolver {

    private static final Logger log =
            LoggerFactory.getLogger(DeliveryZoneResolver.class);

    private final ParcelServiceClient parcelServiceClient;

    public DeliveryZoneResolver(ParcelServiceClient parcelServiceClient) {
        this.parcelServiceClient = parcelServiceClient;
    }

    /**
     * Resolves a customer-provided location string against active delivery zones.
     *
     * @param locationQuery free-text location query provided by the customer
     * @return {@link ZoneResolutionResult} representing the resolution state
     */
    public ZoneResolutionResult resolveZone(String locationQuery) {
        if (locationQuery == null || locationQuery.trim().isBlank()) {
            return ZoneResolutionResult.missingLocation("Location is required. Please provide a city, district, or area name.");
        }

        String clean = locationQuery.trim();
        log.debug("Resolving delivery zone for location query: '{}'", clean);

        List<PublicDeliveryZoneResponse> zones;
        try {
            zones = parcelServiceClient.getPublicZones(clean, null);

            // If no match found, attempt fallback by stripping noise words (e.g. "Colombo District" -> "Colombo")
            if (zones.isEmpty()) {
                String stripped = clean.replaceAll("(?i)\\b(district|city|metro|area|town|province)\\b", "").trim();
                if (!stripped.isEmpty() && !stripped.equalsIgnoreCase(clean)) {
                    log.debug("Retrying zone lookup with stripped term: '{}'", stripped);
                    zones = parcelServiceClient.getPublicZones(stripped, null);
                }
            }
        } catch (RestClientException ex) {
            log.error("Zone lookup failed due to downstream Parcel Service error: {}", ex.getMessage(), ex);
            return ZoneResolutionResult.unavailable("Live SwiftDrop delivery zone lookup service is temporarily unavailable.");
        }

        if (zones.isEmpty()) {
            return ZoneResolutionResult.notFound(clean,
                    "No delivery zone found matching '" + clean + "'. Please specify another location or nearby major city.");
        }

        if (zones.size() == 1) {
            return ZoneResolutionResult.resolved(zones.get(0));
        }

        // Multiple zones returned. Check for an exact case-insensitive match on code, name, or numeric ID
        List<PublicDeliveryZoneResponse> exactMatches = zones.stream()
                .filter(z -> z.zoneCode().equalsIgnoreCase(clean)
                        || z.zoneName().equalsIgnoreCase(clean)
                        || String.valueOf(z.id()).equals(clean))
                .toList();

        if (exactMatches.size() == 1) {
            return ZoneResolutionResult.resolved(exactMatches.get(0));
        }

        log.info("Location query '{}' matched {} candidate zones (ambiguous)", clean, zones.size());
        return ZoneResolutionResult.ambiguous(clean, zones,
                "Multiple delivery zones match '" + clean + "'. Please choose one from the available options.");
    }
}

