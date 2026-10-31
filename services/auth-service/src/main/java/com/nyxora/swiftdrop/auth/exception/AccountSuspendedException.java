package com.nyxora.swiftdrop.auth.exception;

/**
 * Thrown when a suspended user attempts to authenticate.
 */
public class AccountSuspendedException extends RuntimeException {

    public AccountSuspendedException(String message) {
        super(message);
    }
}

