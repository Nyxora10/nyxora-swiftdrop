package com.nyxora.swiftdrop.parcel.domain.enums;

/**
 * Classifies a delivery route for default pricing resolution.
 */
public enum RouteType {
    SAME_ZONE,
    DIFFERENT_ZONE;

    public static RouteType between(Integer originZoneId, Integer destinationZoneId) {
        return originZoneId.equals(destinationZoneId) ? SAME_ZONE : DIFFERENT_ZONE;
    }
}
