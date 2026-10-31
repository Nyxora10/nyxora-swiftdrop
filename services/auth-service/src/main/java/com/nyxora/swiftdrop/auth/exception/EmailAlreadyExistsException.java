package com.nyxora.swiftdrop.auth.exception;

/**
 * Thrown when an incoming registration request attempts to use an email that is already registered.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("An account with email '" + email + "' already exists.");
    }
}

