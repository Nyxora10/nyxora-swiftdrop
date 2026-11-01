package com.nyxora.swiftdrop.auth.dto.response;

import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;

import java.util.UUID;

/**
 * Response returned following an administrative action (approval, rejection, suspension).
 */
public record AdminDecisionResponse(
        String message,
        UUID userId,
        String email,
        RoleName role,
        UserStatus status,
        String statusReason
) {
    public static AdminDecisionResponse of(String message, User user) {
        return new AdminDecisionResponse(
                message,
                user.getId(),
                user.getEmail(),
                user.getRole().getName(),
                user.getStatus(),
                user.getStatusReason()
        );
    }
}

