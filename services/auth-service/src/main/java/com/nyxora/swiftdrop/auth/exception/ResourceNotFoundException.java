package com.nyxora.swiftdrop.auth.exception;

/**
 * Thrown when a requested resource (e.g. User, Role) is not found in the database.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}

