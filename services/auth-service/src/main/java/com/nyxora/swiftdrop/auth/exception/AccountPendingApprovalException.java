package com.nyxora.swiftdrop.auth.exception;

/**
 * Thrown when a Merchant or Courier attempts to authenticate before an administrator has approved their account.
 */
public class AccountPendingApprovalException extends RuntimeException {

    public AccountPendingApprovalException(String message) {
        super(message);
    }
}

