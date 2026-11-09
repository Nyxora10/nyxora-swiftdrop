package com.nyxora.swiftdrop.parcel.dispatch.exception;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;

/**
 * Thrown when a courier assignment is attempted on a parcel that is not in CREATED status.
 */
public class InvalidParcelStateException extends RuntimeException {

    public InvalidParcelStateException(String trackingNumber, ParcelStatus currentStatus) {
        super(String.format("Parcel %s is in status %s. Only parcels in CREATED status can be assigned to a courier.",
                trackingNumber, currentStatus));
    }
}
