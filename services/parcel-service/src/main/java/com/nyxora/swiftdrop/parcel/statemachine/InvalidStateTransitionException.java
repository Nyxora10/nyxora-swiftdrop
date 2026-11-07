package com.nyxora.swiftdrop.parcel.statemachine;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import lombok.Getter;

/**
 * Thrown when an illegal lifecycle transition is attempted on a parcel.
 * Mapped to HTTP 409 Conflict in {@link com.nyxora.swiftdrop.parcel.exception.GlobalExceptionHandler}.
 */
@Getter
public class InvalidStateTransitionException extends RuntimeException {

    private final ParcelStatus fromStatus;
    private final ParcelStatus toStatus;

    public InvalidStateTransitionException(ParcelStatus fromStatus, ParcelStatus toStatus, String message) {
        super(message);
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public InvalidStateTransitionException(String message) {
        super(message);
        this.fromStatus = null;
        this.toStatus = null;
    }
}