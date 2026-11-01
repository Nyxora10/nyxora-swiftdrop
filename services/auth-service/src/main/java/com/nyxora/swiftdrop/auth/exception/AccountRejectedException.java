package com.nyxora.swiftdrop.auth.exception;

/**
 * Thrown when a user whose account application was rejected attempts to authenticate.
 */
public class AccountRejectedException extends RuntimeException {

    public AccountRejectedException(String message) {
        super(message);
    }
}

