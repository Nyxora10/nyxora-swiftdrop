package com.nyxora.swiftdrop.auth.dto;

import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;

/**
 * Normalized user identity extracted from any third-party social provider
 * (Google, Apple, Facebook).
 */
public record SocialUserProfile(
        AuthProvider provider,
        String providerId,
        String email,
        String fullName
) {}
