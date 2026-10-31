package com.nyxora.swiftdrop.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Type-safe configuration properties for SwiftDrop JWT token issuance & verification.
 * Mapped to prefix 'app.jwt' in application.yml.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * Secret key (at least 256 bits / 32 bytes) used for signing HMAC-SHA256 tokens.
     */
    private String secret;

    /**
     * Lifespan of the stateless access token in milliseconds (default: 15 minutes = 900,000 ms).
     */
    private long accessTokenExpirationMs = 900000;

    /**
     * Lifespan of the database-backed refresh token in milliseconds (default: 7 days = 604,800,000 ms).
     */
    private long refreshTokenExpirationMs = 604800000;
}

