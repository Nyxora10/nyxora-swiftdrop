package com.nyxora.swiftdrop.auth.exception;

/**
 * Thrown when an invalid, expired, revoked, or non-existent refresh token is supplied.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}

