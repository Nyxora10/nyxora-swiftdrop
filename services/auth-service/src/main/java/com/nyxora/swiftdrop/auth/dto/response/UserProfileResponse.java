package com.nyxora.swiftdrop.auth.dto.response;

import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Detailed profile summary of the currently authenticated user.
 */
public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        String phoneNumber,
        RoleName role,
        UserStatus status,
        AuthProvider authProvider,
        Instant createdAt
) {
    public static UserProfileResponse fromEntity(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getRole().getName(),
                user.getStatus(),
                user.getAuthProvider(),
                user.getCreatedAt()
        );
    }
}

