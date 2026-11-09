package com.nyxora.swiftdrop.ai_assistant_service.security;

/**
 * Immutable trusted user identity extracted from verified credentials.
 */
public record UserIdentity(
        String userId,
        String role,
        String email
) {
    public boolean hasRole(String targetRole) {
        return role != null && role.equalsIgnoreCase(targetRole);
    }
}

