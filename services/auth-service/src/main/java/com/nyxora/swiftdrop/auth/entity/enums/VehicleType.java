package com.nyxora.swiftdrop.auth.entity.enums;

/**
 * Types of courier delivery vehicles recognized by the platform.
 */
public enum VehicleType {
    /**
     * Motorcycle / Scooter (ideal for lightweight parcels up to 5kg).
     */
    MOTORCYCLE,

    /**
     * Three-wheeler / Tuk-Tuk (parcels up to 25kg).
     */
    THREE_WHEELER,

    /**
     * Cargo Van (medium shipments up to 150kg).
     */
    VAN,

    /**
     * Light / Heavy Cargo Truck (bulk merchant shipments > 150kg).
     */
    TRUCK
}

