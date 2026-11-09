package com.nyxora.swiftdrop.parcel.exception;

/** Raised when an admin mutation violates a unique business constraint. */
public class ResourceConflictException extends IllegalArgumentException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
