package com.nyxora.swiftdrop.parcel.domain.enums;

/**
 * Finite state machine statuses for a parcel's lifecycle.
 * Transitions are managed by the Courier (via status update endpoints)
 * and the Dispatcher (automated assignment and failure detection).
 *
 * <pre>
 * CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED
 *                                                       ↘ FAILED
 * </pre>
 */
public enum ParcelStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED
}

