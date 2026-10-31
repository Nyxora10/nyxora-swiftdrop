package com.nyxora.swiftdrop.auth.entity.enums;

/**
 * Standard Role-Based Access Control (RBAC) roles for SwiftDrop.
 * Follows Spring Security naming conventions with the 'ROLE_' prefix.
 */
public enum RoleName {
    /**
     * Standard retail/individual delivery customer.
     */
    ROLE_CUSTOMER,

    /**
     * High-volume e-commerce merchant using automated order APIs & webhooks.
     */
    ROLE_MERCHANT,

    /**
     * Delivery courier / rider executing parcel pickup and drop-off runs.
     */
    ROLE_COURIER,

    /**
     * Platform administrator / dispatcher managing pricing, zones, approvals, and system state.
     */
    ROLE_ADMIN
}

