package com.nyxora.swiftdrop.parcel.dispatch.exception;

import java.util.UUID;

/**
 * Thrown when attempting to assign a parcel to a courier who has reached
 * the maximum concurrent workload capacity of 20 active deliveries.
 */
public class CourierWorkloadExceededException extends RuntimeException {

    private final UUID courierId;
    private final long currentActiveCount;

    public CourierWorkloadExceededException(UUID courierId, long currentActiveCount) {
        super(String.format("Courier %s has reached the maximum capacity limit of 20 active parcels. Current active: %d.",
                courierId, currentActiveCount));
        this.courierId = courierId;
        this.currentActiveCount = currentActiveCount;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public long getCurrentActiveCount() {
        return currentActiveCount;
    }
}
