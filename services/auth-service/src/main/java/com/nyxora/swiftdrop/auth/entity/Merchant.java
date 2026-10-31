package com.nyxora.swiftdrop.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Merchant business profile entity linked 1:1 to a User account.
 * Manages webhook endpoints and machine-to-machine OAuth 2.0 client credentials.
 */
@Entity
@Table(name = "merchants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Merchant extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @Column(name = "registration_number", nullable = false, unique = true, length = 100)
    private String registrationNumber;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    /**
     * Shared HMAC-SHA256 signing secret for outbound webhook verification.
     */
    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    @Column(name = "oauth_client_id", unique = true, length = 100)
    private String oauthClientId;

    @Column(name = "oauth_client_secret_hash", length = 255)
    private String oauthClientSecretHash;
}

