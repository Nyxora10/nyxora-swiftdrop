package com.nyxora.swiftdrop.auth.dto.response;

import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary view of a user for the administrator's paginated dashboard.
 */
public record AdminUserSummaryResponse(
        UUID id,
        String email,
        String fullName,
        String phoneNumber,
        RoleName role,
        UserStatus status,
        String statusReason,
        AuthProvider authProvider,
        Instant createdAt
) {
    public static AdminUserSummaryResponse fromEntity(User user) {
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getRole().getName(),
                user.getStatus(),
                user.getStatusReason(),
                user.getAuthProvider(),
                user.getCreatedAt()
        );
    }
}

