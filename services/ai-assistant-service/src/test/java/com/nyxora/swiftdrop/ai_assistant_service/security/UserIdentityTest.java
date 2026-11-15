package com.nyxora.swiftdrop.ai_assistant_service.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserIdentityTest {

    @Test
    @DisplayName("Should correctly evaluate hasRole with case-insensitivity")
    void shouldEvaluateHasRole() {
        UserIdentity admin = new UserIdentity("user-1", "ROLE_ADMIN", "admin@swiftdrop.lk");

        assertThat(admin.userId()).isEqualTo("user-1");
        assertThat(admin.role()).isEqualTo("ROLE_ADMIN");
        assertThat(admin.email()).isEqualTo("admin@swiftdrop.lk");

        assertThat(admin.hasRole("ROLE_ADMIN")).isTrue();
        assertThat(admin.hasRole("role_admin")).isTrue();
        assertThat(admin.hasRole("ROLE_USER")).isFalse();
        assertThat(admin.hasRole(null)).isFalse();
    }

    @Test
    @DisplayName("Should return false for hasRole when user role is null")
    void shouldReturnFalseWhenRoleIsNull() {
        UserIdentity noRoleUser = new UserIdentity("user-2", null, "norole@swiftdrop.lk");

        assertThat(noRoleUser.hasRole("ROLE_ADMIN")).isFalse();
        assertThat(noRoleUser.hasRole("USER")).isFalse();
    }
}
