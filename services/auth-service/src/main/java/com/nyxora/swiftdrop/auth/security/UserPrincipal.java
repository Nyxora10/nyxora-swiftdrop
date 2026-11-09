package com.nyxora.swiftdrop.auth.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security UserDetails adapter wrapping the SwiftDrop User entity.
 * Represents the authenticated principal within the SecurityContext.
 * Optionally carries courierId / merchantId for downstream JWT claim embedding (Task 2.5).
 */
@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;

    @JsonIgnore
    private final String password;

    private final String fullName;
    private final String phoneNumber;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    /** Nullable — populated only for ROLE_COURIER authenticated principals (Task 2.5). */
    private final UUID courierId;

    /** Nullable — populated only for ROLE_MERCHANT authenticated principals (Task 2.5). */
    private final UUID merchantId;

    /**
     * Creates a principal without courier/merchant ID enrichment.
     * Used for Customer, Admin, and social-auth registrations.
     */
    public static UserPrincipal create(User user) {
        return create(user, null, null);
    }

    /**
     * Creates a principal with optional courier/merchant ID enrichment for JWT claim embedding.
     * Pass non-null courierId for ROLE_COURIER users; non-null merchantId for ROLE_MERCHANT users.
     */
    public static UserPrincipal create(User user, UUID courierId, UUID merchantId) {
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(user.getRole().getName().name())
        );

        return UserPrincipal.builder()
                .id(user.getId())
                .email(user.getEmail())
                .password(user.getPasswordHash())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .active(user.getStatus() == UserStatus.ACTIVE)
                .authorities(authorities)
                .courierId(courierId)
                .merchantId(merchantId)
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}

