package com.nyxora.swiftdrop.auth.dto.response;

import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;

import java.util.UUID;

/**
 * Response for accounts requiring administrator verification before active login.
 */
public record RegistrationResponse(
        String message,
        UUID userId,
        String email,
        RoleName role,
        UserStatus status
) {}

