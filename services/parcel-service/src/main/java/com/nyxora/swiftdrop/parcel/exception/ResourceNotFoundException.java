package com.nyxora.swiftdrop.parcel.exception;

/** Raised when an admin references a zone or pricing rule that does not exist. */
public class ResourceNotFoundException extends IllegalArgumentException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
