package com.nyxora.swiftdrop.auth.entity.enums;

/**
 * Authentication identity provider for a user account.
 */
public enum AuthProvider {
    /**
     * Local authentication using email and BCrypt-hashed password.
     */
    LOCAL,

    /**
     * Social single sign-on via Google Identity Services / OAuth 2.0.
     */
    GOOGLE,

    /**
     * Social single sign-on via Apple ID / OpenID Connect.
     */
    APPLE,

    /**
     * Social single sign-on via Facebook Login / Graph API.
     */
    FACEBOOK
}
