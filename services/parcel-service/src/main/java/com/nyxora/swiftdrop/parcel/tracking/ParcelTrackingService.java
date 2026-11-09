package com.nyxora.swiftdrop.parcel.tracking;

import com.nyxora.swiftdrop.parcel.tracking.dto.ParcelTrackingResponse;

/**
 * Service contract for public parcel tracking operations.
 */
public interface ParcelTrackingService {

    /**
     * Retrieves the chronological tracking history and current delivery status
     * for a given linear tracking number, with recipient PII masked for privacy.
     *
     * @param trackingNumber linear tracking code (e.g. SD-20261107-7K9P2X)
     * @return tracking response with timeline and masked recipient data
     * @throws IllegalArgumentException if tracking number is not found
     */
    ParcelTrackingResponse trackParcel(String trackingNumber);
}

