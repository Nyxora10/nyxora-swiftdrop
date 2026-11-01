package com.nyxora.swiftdrop.auth.entity;

import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Core User entity for identity and authentication in SwiftDrop.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    // BCrypt-hashed password. Can be null for third-party social logins (e.g. Google OAuth 2.0).
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    // Feedback or justification provided by an administrator on rejection or suspension.
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 30)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    // Unique identifier provided by third-party identity provider (e.g. Google OAuth 2.0 'sub' claim).
    @Column(name = "provider_id", unique = true, length = 100)
    private String providerId;
}
