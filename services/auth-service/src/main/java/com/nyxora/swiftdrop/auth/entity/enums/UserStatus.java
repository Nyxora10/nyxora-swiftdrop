package com.nyxora.swiftdrop.auth.entity.enums;

/**
 * Account lifecycle status for SwiftDrop users.
 */
public enum UserStatus {
    // Account is active and authorized to log in.
    ACTIVE,

    // Account created by Merchant or Courier awaiting Administrator review and
    // approval.
    PENDING_APPROVAL,

    // Account suspended by Administrator due to policy violation or investigation.
    SUSPENDED,

    // Account application rejected by Administrator during review (requires
    // statusReason).
    REJECTED;
}
