package com.nyxora.swiftdrop.ai_assistant_service.service;

import com.nyxora.swiftdrop.ai_assistant_service.dto.PublicDeliveryZoneResponse;

import java.util.List;

/**
 * Outcome of attempting to resolve a customer-provided location string
 * to a verified Sri Lankan delivery zone.
 */
public record ZoneResolutionResult(
        Status status,
        PublicDeliveryZoneResponse matchedZone,
        List<PublicDeliveryZoneResponse> candidateZones,
        String message
) {
    public enum Status {
        /** Exactly one unambiguous zone matched. */
        RESOLVED,
        /** Multiple zones match; customer clarification is required. */
        AMBIGUOUS,
        /** No zones found for the given location text. */
        NOT_FOUND,
        /** Customer omitted or provided an empty location query. */
        MISSING_LOCATION,
        /** Downstream Parcel Service or zone directory is unavailable. */
        UNAVAILABLE
    }

    public static ZoneResolutionResult resolved(PublicDeliveryZoneResponse zone) {
        return new ZoneResolutionResult(Status.RESOLVED, zone, List.of(), "Zone resolved: " + zone.zoneName());
    }

    public static ZoneResolutionResult ambiguous(String locationQuery, List<PublicDeliveryZoneResponse> candidateZones, String message) {
        return new ZoneResolutionResult(Status.AMBIGUOUS, null, candidateZones, message);
    }

    public static ZoneResolutionResult notFound(String locationQuery, String message) {
        return new ZoneResolutionResult(Status.NOT_FOUND, null, List.of(), message);
    }

    public static ZoneResolutionResult missingLocation(String message) {
        return new ZoneResolutionResult(Status.MISSING_LOCATION, null, List.of(), message);
    }

    public static ZoneResolutionResult unavailable(String message) {
        return new ZoneResolutionResult(Status.UNAVAILABLE, null, List.of(), message);
    }
}

